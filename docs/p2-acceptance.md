# P2 验收记录：单题训练与重答闭环

完成日期：2026-10-03（Asia/Shanghai）。范围为策划书 P2；P3–P6 未实施。

## 交付与使用

沿用 P1 的 24 道题、72 个知识点/评分要点和版本化资料，不修改固定样例的预期标签。正常启动后，从侧栏“八股题库”进入：

| 页面 | 路由 | 行为 |
| --- | --- | --- |
| 八股题库 | `/practice/questions` | 主题、难度筛选与分页，选题建立会话 |
| 单题训练 | `/practice/sessions/:id` | 文字回答、逐项反馈、引用核对、解析、重答对比、专项追问 |
| 我的练习 | `/practice/history` | 保存的回答、评估状态、历史会话与继续练习 |

首次提交前不自动展示解析。用户可主动使用表达提示或提前看解析；服务端记录“有辅助”。首答、重答各自保存原文与辅助快照，查看解析不会把既有独立首答改为有辅助。专项追问选择错误、缺失、部分覆盖、无法判断要点，要求分析一个易错说法与具体场景，沿用所选要点的版本和来源；继承已使用的辅助状态。

反馈显示五种判定、候选人原话、判断理由、表达改进及官方依据，不提供虚构总分。重答按要点 ID 对比新补齐、已纠正、保持正确、退步、仍待解决与不可判断。解析后的改进明确区别于长期掌握。

## 数据与任务约束

新增 `practice_session`、`practice_attempt`、`practice_evaluation` 三张表。沿用现有 Hibernate `ddl-auto: update` 初始化方式，不删除或迁移原简历、面试表；专用集成测试使用独立数据库的 `create-drop`，不可指向日常数据。

- 会话保存完整题目、评分标准、知识点与来源快照。每次回答保存父回答、原文、TEXT 输入模式、辅助标志和客户端请求 ID；不覆盖原回答。
- 回答与 `PENDING` 任务在同一事务提交。相同请求 ID/相同内容返回既有记录；同 ID 不同内容返回 409。会话行锁、数据库唯一约束与提交完成后释放的创建锁处理并发重复请求。
- 状态为 `PENDING → RUNNING → SUCCEEDED / FAILED`。调用模型前结束短数据库事务；2 个工作线程、4 个排队位置，数据库待处理任务不会因线程队列满而丢失。
- 连接超时 5 秒、单次请求超时 45 秒。失败不自动调用付费模型；每条回答最多 3 代评估（首次 + 2 次手动重试）。保留失败代次、错误类别、参考快照、模型标识、提示词版本与耗时。
- 重启后 `RUNNING` 标为可重试的 `INTERRUPTED`，`PENDING` 继续派发。成功结果不能被静默重评覆盖；评估中不能结束会话，结束后不能新增回答或重试。
- 前端保存本浏览器草稿和待提交请求，发送结果不明确时用原请求 ID 重发。服务端回答与进度可刷新恢复、离开后回看。浏览器草稿依赖本地存储，不代替服务端持久化。

这是个人自部署、单应用实例的实现。启动恢复逻辑与创建锁未设计为多副本调度器；公开多用户服务所需的身份验证、所有权隔离仍属于策划书的独立扩展任务。

## RAG 与模型判断

生产评估使用 `PracticeEvaluationEngine`，结合固定评分依据、相关 Chroma 检索片段与回答。只接收绑定本题来源、版本一致且内容能在快照中核对的检索结果，检索相似度不转为答案分数。

| 参考状态 | 行为 |
| --- | --- |
| AVAILABLE | 展示已结合核对资料及相关检索片段 |
| NO_MATCH | 明确未命中相关资料，基于已核验题目资料有限评估 |
| UNAVAILABLE | 明确知识检索不可用，基于已核验题目资料有限评估 |
| 要点无可用核验来源 | 只能 `UNCERTAIN`；全题无来源时程序直接生成无法判断结果，不调用模型 |

沿用 P1 严格解析器：问题/评分版本、回答 hash、要点完整性、真实连续原话、来源 ID/修订 hash、真实来源引用均需验证。空白、截断、错误结构或伪造引用不作为成功结果保存。

当前提示词 `p2-criteria-v5`；实际验收模型 `deepseek-flash`，运行时仍读取已有 `DEEPSEEK_*` 配置。训练适配器使用 Java HttpClient 显式请求 JSON 对象、开启 thinking、`reasoning_effort=low`、`max_tokens=8000`，单次 HTTP 调用。原模拟面试模型接入保持兼容。

参数与输出行为核对了 DeepSeek 官方的 [JSON 输出](https://api-docs.deepseek.com/guides/json_mode/)、[思考模式](https://api-docs.deepseek.com/guides/thinking_mode/)及 [Chat Completion 接口](https://api-docs.deepseek.com/api/create-chat-completion/)。请求失败只向用户返回安全错误，不显示密钥或供应商响应正文。

## 固定样例实际评测

数据为 P1 `evaluation-cases-v1.json` 的 24 条人工预期样例，每条 3 个要点；覆盖正确口语表达、缺失、事实错误、无关回答、错误术语与证据不足。生产和评测共用同一评估引擎与校验器。

最终记录：[p2-model-evaluation.json](p2-model-evaluation.json)。

| 指标 | 实测结果 |
| --- | --- |
| 样例 / 要点 | 24 / 72 |
| 实际模型请求 | 20 次；另外 4 条无证据样例由程序标为 UNCERTAIN |
| 通过结构与证据校验的结果 | 24 条（含 4 条程序结果） |
| 与预期相同的要点判定 | 72 / 72 |
| 20 次模型调用耗时 | 最短 3351 ms，中位 5612 ms，最长 11770 ms |

校准过程中实际发现过空白/截断输出、MISSING 漏引资料、事实错误被判缺失、完整口语表达被判部分覆盖。通过逐项依据模板、错误优先判断、先写具体理由及显式思考参数修正；非法输出仍由程序拒绝，不通过放宽校验掩盖错误。

**这 24 条同时用于提示词校准，属于已知固定集，不是独立测试集，也不是真实用户答案的准确率承诺。** 未见答案的判断质量与长期学习效果未在本集测量；每次实际调用仍可能失败或误判。

## 工程与页面验收

| 检查 | 结果与证据 |
| --- | --- |
| 后端回归 | 131 项：130 通过、0 失败/错误、1 跳过。跳过的是未启用的 P1 独立 MySQL 测试；P2 的真实 MySQL 8 项已通过 |
| 数据流程 | H2 8 项及真实 MySQL 8 项：不可变首答、辅助快照、并发幂等、失败/重试上限、中断恢复、来源不足、纠错对比与追问 |
| 实际 Chroma | RAG 4 项与 P1 题库映射 1 项通过，使用真实本地中文向量模型及 Chroma 1.4.4 |
| 模型协议 | 实际模型固定集通过；本地 HTTP 测试验证参数、空输出、截断及供应商错误处理 |
| HTTP 闭环 | `p2-http-acceptance.ps1` PASS：首答、去重、解析、重答、2 个新补齐项、追问、历史与结束；非法输入 400/409 |
| 前端 | TypeScript 检查与生产构建通过 |
| 桌面浏览器 | Codex 内置 Chromium：锁题首答→反馈→解析→有辅助重答→2 项新补齐→专项追问；草稿刷新、评估刷新与历史恢复通过 |
| 手机布局 | 同一 Chromium 的 390×844 视口：Lua 题选题→首答→反馈→解析→重答→3 项新补齐→单要点追问→结束；无横向溢出 |
| 故障恢复 | 手机首答遇到实际不可达模型地址，失败保留原文；重启应用、恢复模型后，手动重试成功，仍是同一回答的第 2 代评估 |
| 检索故障 | 暂停专用 Chroma 后，手机实际反馈显示“知识检索不可用”，使用核验资料有限评估；无资料 UNCERTAIN 路径由固定集与业务测试验证 |
| 原功能兼容 | 原面试状态机/编排/接口回归通过；浏览器验证上传入口、历史面试对话、简历查看与报告深链接。历史页面使用明确标注的虚构验收记录，未以它冒充新生成模型报告 |

浏览器手机验收是窄视口验证，未声称完成真实手机设备、麦克风或录音测试。页面验收修正了手机“结束练习”入口与追问题干泄露正确答案的问题，也核对了无资料时的提示；最终定向流程/依据 10 项、前端构建及后端打包再次通过。

截图：[桌面重答对比](screenshots/p2-desktop.jpg)、[手机重答对比](screenshots/p2-mobile.jpg)、[手机评估失败](screenshots/p2-mobile-failure.jpg)、[已完成专项追问](screenshots/p2-followup.jpg)；另保存 [HTTP 验收数据](p2-http-acceptance.json)。本次回答均为构造的技术练习文本，未发送用户简历。

## 复现与接口

正常使用仍按 README 的 Docker Compose 启动，使用现有 DeepSeek 和知识库配置。`practice.jobs.enabled` 默认开启；未配置可用模型时保留回答并显示评估失败，不伪造反馈。

基础检查：

```bash
cd backend
mvn test
cd ../frontend
npm run build
```

默认检查中外部集成项按环境变量跳过。实际服务检查需另行启用：

| 环境变量 | 用途 |
| --- | --- |
| `P2_MYSQL_TEST_URL`、`P2_MYSQL_TEST_USER`、`P2_MYSQL_TEST_PASSWORD` | P2 专用 MySQL 集成数据库，测试将创建和删除题库/训练表，只能使用可丢弃的测试库 |
| `RAG_CHROMA_TEST_URL`、`P1_CHROMA_TEST_URL` | 本地实际 Chroma；测试使用独立随机集合并清理 |
| `P2_MODEL_TEST_KEY`、`P2_MODEL_TEST_BASE_URL`、`P2_MODEL_TEST_NAME` | 固定样例真实模型评测；会实际调用供应商，密钥不写入报告 |

`PracticeModelIntegrationTest` 的原始报告输出到 `backend/target/p2-model-evaluation.json`，逐项摘要另保存到本文链接的 docs 文件。业务测试使用模型替身验证状态和约束；真实反馈质量以单列的模型评测为证据。

专用 Windows HTTP 启动脚本 `backend/src/test/scripts/start-p2-acceptance.ps1` 使用本机 JDK 路径、13316/18000/18080 隔离端口与 `p2_acceptance` 库，适合本次验收环境，不替代正常部署。`p2-http-acceptance.ps1` 保存结果到 `backend/target/p2-http-acceptance.json`。

| 方法 / 路径 | 参数与说明 |
| --- | --- |
| POST `/api/practice/sessions` | `{questionId, questionVersion?, clientRequestId}` |
| GET `/api/practice/sessions?page=0&size=12` | 练习历史，size 1–50 |
| GET `/api/practice/sessions/{id}` | 公共题干、回答、评估代次、对比与恢复状态 |
| POST `/api/practice/sessions/{id}/attempts` | `{answer, parentAttemptId, inputMode:"TEXT", clientRequestId}`；首答父 ID 为 null，重答引用最新成功回答 |
| POST `/api/practice/sessions/{id}/hints` | 记录辅助，返回表达结构提示 |
| GET `/api/practice/sessions/{id}/reference?assisted=true` | 首答前需显式 assisted=true；记录已看解析 |
| POST `/api/practice/sessions/{id}/complete` | 幂等结束，不允许任务仍在处理中 |
| GET `/api/practice/attempts/{id}` | 回答、各代评估、最新结果及对比 |
| GET `/api/practice/attempts/{id}/reference` | 首答后查看解析 |
| POST `/api/practice/attempts/{id}/retry-evaluation` | `{clientRequestId}`，失败评估显式重试 |
| POST `/api/practice/attempts/{id}/followups` | `{clientRequestId}`，建立单要点子练习，返回原练习链接 |

客户端请求 ID 为 8–64 位字母、数字、下划线或短横线，前端使用 UUID；回答为非空的 1–10000 字文字。参数错误 400、状态冲突 409、不存在记录 404。

P2 已形成可使用的文字练习闭环。本次没有新增语音、项目事实卡、复习计划或长期掌握度统计。

验收后已停止专用应用并移除 `coding-p2-mysql-check`、`coding-p2-chroma-check` 测试容器；没有修改其他容器或日常数据库。Windows 打包曾因验收应用占用 jar 而失败，关闭该应用后最终打包通过。截图、评测摘要与 HTTP 证据保留在 docs 中。
