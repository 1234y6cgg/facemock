# 科大讯飞语音使用说明

已实现录音、上传、回放、讯飞 IAT 适配、原始转写保留、修订确认、接入 P2 评估、重答与删除。当前完成代码和本地检查；**真实讯飞转写、桌面及移动设备麦克风验收待补**，见 [验收记录](p3-acceptance.md)。

八股单题训练和完整模拟面试现在统一使用后端的科大讯飞 IAT 适配，不再使用浏览器 Web Speech API。两处共用同一 `.env` 配置、音频格式、最长时长、有限重试和保留策略。完整模拟的新增检查见 [统一语音记录](unified-speech-acceptance.md)。

## 配置和启动

在项目根目录 `.env` 配置以下字段，使用同一讯飞应用中“语音听写 IAT”的凭证：

```dotenv
SPEECH_ENABLED=true
XFYUN_APP_ID=
XFYUN_API_KEY=
XFYUN_API_SECRET=
SPEECH_RETENTION_DAYS=30
SPEECH_TIMEOUT_SECONDS=240
```

密钥只在后端使用，不进入前端。Docker Compose 自动传入这些字段；修改后重新创建应用：`docker compose up -d --build app`。直接启动 Java 时，需要把 `.env` 字段设为进程环境变量，Spring Boot 不会自动读取这个文件。

Docker 原音写入 `speech_recordings` 命名卷中的 `/app/data/recordings`。原生运行默认 `data/recordings`，可用 `SPEECH_STORAGE_PATH` 调整。备份需要同时保留 MySQL 和录音卷；`docker compose down -v` 会删除数据卷。

`GET /api/speech/status` 的 `configured=true` 只表示已启用且三项凭证非空，不表示服务授权、余额和网络已验证。缺少配置时录音入口禁用并说明原因，文字练习仍可使用。

## 用户流程

完整模拟面试：在 `/mock` 开始面试，切换为语音；录音停止后回听，点击“上传录音，讯飞转写”；转写成功后核对并修订，再点击“确认文本，提交回答”，进入原模拟面试的追问流程。停止录音或完成转写不会自动发送回答。录音草稿按面试及问题保存在当前浏览器，已上传任务可刷新恢复；麦克风采集不经过浏览器自带识别服务。单段录音上限 180 秒。

1. 从 `/practice/questions` 选题进入练习，点击“开始录音”，授权该站点使用麦克风。
2. 停止后先回听，上传录音开始转写。上限 180 秒；时长由后端读取音频样本数校验，不能靠修改客户端计时绕过。
3. 转写完成后核对文本。原音和原始转写不随修订改变；只有点击“确认文本，提交评估”后的文本进入 P2。
4. 查看知识反馈、表达建议与原音；成功评估后“再答一次”，保留父回答和同题逐项对比。
5. 转写失败保留录音，允许手动重试；每段录音最多三个任务版本（首次加两次重试），没有自动收费重试。

浏览器需要 HTTPS 或本机 localhost。手机通过局域网普通 HTTP 地址访问不能保证录音可用，须使用可信 HTTPS。AudioWorklet 编码为 16kHz、单声道、16bit PCM WAV；导入文件也必须使用这种规范的 44 字节 WAV 头，暂不支持 MP3、双声道或带额外元数据块的 WAV。浏览器要求见 [getUserMedia 文档](https://developer.mozilla.org/en-US/docs/Web/API/MediaDevices/getUserMedia)及 [AudioWorkletNode 文档](https://developer.mozilla.org/en-US/docs/Web/API/AudioWorkletNode)。

拒绝权限、没有设备或音频过短/无声均明确报错。录制中刷新或离开会中止，须先停止保存；停止后的原音草稿存入当前浏览器 IndexedDB，修订文本与待确认请求保存在 localStorage。网络失败可保留原请求标识重试，刷新可恢复已停止的草稿；清空浏览器数据会清除这些本地副本。

表达反馈分别使用实际录音时长、确认文本字符数与可能的口头词原文定位。字符数不是实际说话字数，位置按 JavaScript/Java UTF-16 索引计算，区间左闭右开；修订可能增删文字，口头词可能有正常语义，需要回听核对。这些指标不参与知识评分，不依据音色或口音作判断。

## IAT 实现与成本边界

使用官方 WSS 地址、HMAC-SHA256 签名、PCM 分帧上传及结束标识。IAT 单会话最长 60 秒，本项目将原音按 50 秒分段顺序识别，最长 180 秒需要至多四个会话；收到完整结束结果才认定成功。协议依据：[讯飞 IAT WebAPI 文档](https://www.xfyun.cn/doc/asr/voicedictation/API.html)。

每个任务版本可能消耗多个调用；三次任务上限意味着最长录音最多十二个分段会话。后段失败后重试会重新处理前段，已经消耗的额度不会退回。具体额度、套餐和费用以 [讯飞控制台](https://console.xfyun.cn/) 为准；本次没有实际计费或识别准确率数据。

目前按固定边界切段，没有基于静音切分或重叠拼接，边界词可能受影响。长停顿可能让供应商提前结束；程序会将未完整接收原音的会话判为失败，避免悄悄截断答案。转写按录音时长发送，三分钟录音转写耗时接近或超过三分钟；单工作线程与两个等待槽控制并发，多段任务总超时默认 240 秒。应用重启将运行中任务标为中断失败，等待中任务继续排队；重试由用户明确触发。

## 存储、到期和删除

原音默认保留 30 天（配置范围 1–365 天）。到期立即禁止回放及确认，并隐藏原始转写；应用启动 10 秒后及每小时清理至多 50 条过期原音、原始转写和回答访问链接。清理异常会记安全错误日志，下一轮继续；应用停机期间不清理物理文件。确认文本和知识反馈仍作为练习记录保存。

“删除这段录音”删除原音和原始转写，保留确认文本及知识反馈；“删除练习”同时删除其追问练习、原音、转写、回答和评估，返回关联 ID 供当前浏览器清理草稿。知识评估等待/运行中时暂时禁止删除整条练习，防止评估产生迟到记录；转写中的录音可删除，迟到的 ASR 结果被丢弃。其他浏览器和外部备份中的副本需在对应位置处理。

文件与 MySQL 不是跨资源原子事务：正常上传回滚会删除新文件，但进程在写文件与提交数据库之间突然退出可能留下孤立文件；删除文件后数据库提交失败也可能导致引用的文件不存在。当前为个人自部署、单应用实例，尚未提供崩溃后的自动孤立文件审计、登录隔离或多实例任务租约，不能直接公开为多人服务。

## 接口

| 请求 | 用途 |
| --- | --- |
| `GET /api/speech/status` | 配置状态、上限及保留策略 |
| `POST /api/interviews/{id}/speech/recordings` | 模拟面试 multipart：turn（当前消息数量）、clientRequestId、file |
| `GET /api/interviews/{id}/speech/recordings` | 模拟面试的录音与转写任务，页面按问题过滤 |
| `POST /api/speech/recordings` | multipart：sessionId、clientRequestId、file；重答附 parentAttemptId |
| `GET /api/speech/recordings?sessionId=...` | 当前练习所有录音与转写任务 |
| `GET /api/speech/recordings/{id}` | 录音状态及不可变原始转写 |
| `GET /api/speech/recordings/{id}/audio` | WAV 回放，响应 `Cache-Control: no-store` |
| `POST /api/speech/recordings/{id}/retry` | JSON：clientRequestId；新增有限次数的任务版本 |
| `POST /api/speech/recordings/{id}/confirm` | JSON：text、clientRequestId；创建 P2 SPEECH 回答及评估任务 |
| `DELETE /api/speech/recordings/{id}` | 删除原音、原始转写及回放链接 |
| `DELETE /api/practice/sessions/{id}` | 删除练习及追问；返回 sessionIds、recordingIds |

录音状态为 ACTIVE/DELETED/EXPIRED；转写状态为 PENDING/RUNNING/SUCCEEDED/FAILED/DELETED/EXPIRED。参数及音频不合规返回 400，容器上传限额返回 413，未配置/过期/父回答冲突/次数上限返回 409，不存在返回 404。服务端仅由已成功转写的录音创建 SPEECH 回答，普通文字接口不能伪造该模式。

模拟面试的录音使用独立会话命名空间，不创建八股练习或 P2 评估。页面核对后通过原 `POST /api/interviews/{id}/answer` 提交确认文本；服务器验证录音上传/重试属于当前等待回答的问题，已经结束的面试不能再上传或重试。删除面试会清除其录音文件及转写任务；八股录音不受影响。模拟转写原始结果可在提交前主动删除；提交后的文字作为面试消息保留。

## 后续真实验收

凭证配置并重启后，使用真实桌面麦克风和至少一个移动设备录制回答，走通“录音→回听→上传→核对→确认→知识反馈→重答对比→回放→删除”，记录浏览器、系统、网络、音频时长、识别错误和耗时。需要实际测试拒绝权限、网络失败与到达上限，手机视口截图不能替代移动设备录音验收。

还提供可选真实供应商检查脚本，从本地 `.env` 读取凭证，上传使用者提供的真实规范 WAV：

```powershell
# 需要 JDK 17 与 Maven；调用可能消耗讯飞额度。
./backend/src/test/scripts/p3-real-asr.ps1 -WavPath 'D:/local/real-answer.wav'
```

通过后写入 `backend/target/p3-real-asr.json`，只含时间、音频/转写摘要、字符数和耗时，不含密钥或转写全文。该检查只证明供应商适配能识别此录音，不能替代页面端到端与设备实测。
