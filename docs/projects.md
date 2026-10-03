# 项目话术训练（P4）

入口：侧栏“项目话术” `/projects`。先从简历选择一个项目，核对事实，再练两分钟介绍、个人职责、技术选型、困难定位、效果证明和复盘改进。

## 使用流程

1. 选择已经解析的简历，或上传文字版 PDF / Word。只导入所选项目的结构化结果；简历提取是草稿，不能直接作为确认事实。
2. 编辑背景、个人贡献、已完成方案、实际难点、结果、证据和未完成改进。缺少信息可以留空。勾选本人核对后保存。
3. 添加文本或 Markdown 说明，注明出处；也可读取本地 `.txt` / `.md` 文件。材料索引完成后开始练习。
4. 独立回答，查看四项反馈及原文引用。存在差异时，核对回答和材料双方原文；材料有误可回项目页修订，再开启新版练习。
5. 在同一问题下重答并比较，或围绕薄弱项进入追问。练习记录保留每次回答、评估版本和当时的材料片段。

每个事实字段最多 3000 字，补充材料单份最多 10000 字，每项目最多 20 份活动材料、合计 50000 字；回答最多 10000 字。事实卡及回答草稿保存在当前浏览器，补充材料编辑框尚未提供刷新恢复。浏览器草稿含本人项目内容，使用自己的浏览器。

## 配置和运行

复用 `.env` 中的 `DEEPSEEK_API_KEY`、`DEEPSEEK_BASE_URL`、`DEEPSEEK_MODEL_NAME`、MySQL、Redis 和 `CHROMA_BASE_URL`。项目索引复用本地 512 维 BGE 模型，不增加嵌入 API 密钥。Redis 用于现有 HTTP 限流；MySQL 保存项目、版本与练习。`docker compose up -d --build` 会包含新页面和后端接口。

P4 为文字项目表达训练；P3 的录音入口仍属于八股单题练习，真实讯飞 IAT 与设备验收状态见 [P3 记录](p3-acceptance.md)。

项目索引独立于公共技术知识库，即使 `KNOWLEDGE_ENABLED=false`，P4 仍需要 Chroma。未确认事实卡不能开始；索引未就绪或 Chroma 不可用返回明确错误，不借用公共知识或另一个项目补全事实。索引失败可以手动重试。

## 来源、版本与删除

每个项目使用单独的 `private_project_<UUID>` 集合。Chroma 查询同时在服务端过滤 `project_id`、`project_revision` 和 `source_kind=material`，返回后再核对来源 ID、版本及原文。不是先检索所有项目再由页面过滤。筛选语义依据 [Chroma 官方元数据过滤说明](https://docs.trychroma.com/docs/querying-collections/metadata-filtering)。

事实卡完整保存在练习快照中，补充材料按题目检索最多六个片段。分块上限 480 字、重叠 40 字。检索范围有限，“当前材料无法判断”仅表示本次片段不足，不等于事实不存在。材料太长时，可以拆成有明确标题和出处的说明。

事实卡保存、材料添加、修订或删除都会增加项目总版本，并形成不可变快照。新版本成功写入后清除 Chroma 中更旧版本，查询始终限定当前版本；索引同步期间禁止开启新版练习。编辑请求带 `expectedRevision`，版本过期返回 409，避免静默覆盖。

删除当前材料创建删除版本，之后不再参与新练习检索；MySQL 旧材料及历史评估引用仍保留。**这不是彻底抹除私人数据的接口**。旧练习的重答、追问继续使用其开练时的固定快照，修订材料后应从项目页开启新练习。当前阶段没有整个项目的删除或历史清除入口。

“私有集合”指单人自部署环境中项目资料与公共技术知识分离。没有新增账号、鉴权或租户机制，不可据此宣称已支持安全的公开多用户服务。

## 反馈边界与任务恢复

固定四项：回答题意、个人贡献、材料支持、材料一致性。模型仅返回有限判定、回答原话、材料引用、待补字段和行动代码；页面把行动代码显示为“可以改进 · 尚未完成”。没有自由生成的指标、职责或改写成品。

校验要求回答哈希一致、四项完整且不重复、原话确实存在、引用 ID/版本和连续原文属于本次片段。冲突须同时给出双方原文；仅有未完成改进不能证明已完成成果或构成冲突。校验失败不会展示成功反馈。**引用校验保证可追溯，不保证模型语义判断正确，也不独立证明项目真实性**。

回答先落库，再异步调用模型，不在数据库事务中等待模型。评估状态为 `PENDING → RUNNING → SUCCEEDED / FAILED`；最多三次评估（首次及两次手动重试）。重启把遗留运行中评估标为中断失败；待处理索引和评估继续被扫描。请求键及数据库约束处理重复提交，页面保留未确认提交的请求键，响应丢失可重试同一回答。

重答须引用最近一次成功评估的回答，不覆盖首答。比较限于同一问题、同一材料快照、同模型和评估规则版本；配置变化时明确说明暂不判断改进。对比只说明本次表达判定变化，不更新长期掌握状态。P4 使用一个后台工作线程及有界队列，适用于单应用实例；当前列表/历史未分页，大量历史及多实例调度留待后续优化。

## 接口

| 方法与路径 | 用途 |
| --- | --- |
| `GET /api/projects`、`GET /api/projects/resumes` | 项目列表、可选择的已解析简历 |
| `POST /api/projects/from-resume` | `{resumeId, projectIndex}`，重复导入返回已有项目 |
| `GET /api/projects/{id}` | 事实卡、材料及索引状态 |
| `PUT /api/projects/{id}/facts` | `{name, facts, expectedRevision}` |
| `POST /api/projects/{id}/materials` | `{title, origin, content, expectedRevision}` |
| `PUT /api/projects/{id}/materials/{materialId}` | 修订材料，增加材料与项目版本 |
| `DELETE /api/projects/{id}/materials/{materialId}?expectedRevision=N` | 删除当前材料，保留历史 |
| `POST /api/projects/{id}/retry-index` | 重试失败索引 |
| `POST /api/projects/{id}/sessions` | `{template, expectedRevision, clientRequestId}` |
| `GET /api/projects/{id}/sessions`、`GET /api/projects/sessions/{id}` | 历史、完整训练快照 |
| `POST /api/projects/sessions/{id}/answers` | `{answer, parentAttemptId, clientRequestId}` |
| `POST /api/projects/attempts/{id}/retry`、`/followup` | `{clientRequestId}`，重试评估/生成专项追问 |
| `POST /api/projects/sessions/{id}/complete` | 结束训练（须等待评估完成） |

模板 ID：`INTRO / OWNERSHIP / CHOICE / DEBUGGING / IMPACT / REFLECTION`。追问由服务端根据薄弱判定和回答原话选取受约束模板，固定材料范围，不让模型自由引入项目事实。

## 复现检查

常规后端检查：JDK 17 下 `cd backend && mvn test`；前端 `cd frontend && npm run build`。外部服务检查按下表显式启用，不会自动读取 `.env` 密钥。

| 检查类 | 环境变量 |
| --- | --- |
| `ProjectMysqlIntegrationTest` | `P4_MYSQL_TEST_URL / USER / PASSWORD` |
| `ProjectChromaIntegrationTest` | `P4_CHROMA_TEST_URL` |
| `ProjectModelIntegrationTest` | `P4_MODEL_TEST=true`、`P4_MODEL_KEY / NAME / BASE_URL` |

MySQL 测试使用 `create-drop`，会创建/删除应用实体表，必须使用专门的可丢弃测试数据库。Chroma 测试创建随机项目集合并清理自己的集合。模型测试使用仓库中的七条虚构事实边界样例，实际请求模型并产生调用消耗，输出 `backend/target/p4-model-evaluation.json`，不输出密钥。

验收证据和局限见 [P4 验收记录](p4-acceptance.md)。
