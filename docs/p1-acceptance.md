# P1 验收记录：精选题库与评分依据

验收日期：2026-10-02　｜　结果：通过　｜　对应策划书：[interview-training-plan.md](interview-training-plan.md)

本次完成 P1 的后端题库与评估校验基础。题库可真实导入 MySQL，并将来源资料索引到 ChromaDB；P2 的训练页面、回答保存、模型逐项评分和重答闭环仍待实施。本阶段没有调用收费模型，固定样例的预期标签不能视为模型准确率。

## 交付范围

| 内容 | 结果 |
| --- | --- |
| 精选题目 | 24 道；Redis、MySQL、Spring、Kafka、Java 线程池、JVM 各 4 道 |
| 评分依据 | 72 个稳定知识点、72 个评分要点，包含可接受表述、易错点、来源和追问 |
| 来源资料 | 24 篇版本化知识摘要，绑定官方链接和核对日期 |
| 固定回答 | 24 条有预期逐项判定和原话证据的样例；六种情况各 4 条 |
| 查询接口 | 主题与难度筛选、分页、详情及指定版本查询 |
| 评估契约 | 独立 DTO、五种要点状态、严格 JSON 与引用校验 |

内容入口：

- 题库：`backend/src/main/resources/questions/catalog-v1.json`。
- 样例：`backend/src/test/resources/questions/answer-cases-v1.json`。
- 导入与查询：`backend/src/main/java/com/mockinterview/service/question/`。
- 评估契约：`backend/src/main/java/com/mockinterview/capability/evaluation/`。

## 初始化、版本与历史引用

沿用项目现有的 JPA `ddl-auto=update` 初始化方式，增加五张表：`training_question`、`training_question_revision`、`training_knowledge_point`、`training_rubric_criterion`、`training_source_revision`。P1 没有迁移或清除已有简历、面试和报告数据。

启动时读取仓库题库资源，在单个数据库事务中完成导入，提交后再生成向量和写入 ChromaDB。重复导入相同内容不增加题目、修订或来源；冲突会回滚整批数据库导入。ChromaDB 故障不会撤销已提交的题库，恢复服务后重启应用可补建索引。

| 配置 | 默认值 | 行为 |
| --- | --- | --- |
| `QUESTIONS_SEED_ON_STARTUP` | `true` | 启动时导入题库；设为 false 时不导入也不触发题库索引 |
| `QUESTIONS_INDEX_KNOWLEDGE` | `true` | 导入成功后索引题目来源 |
| `KNOWLEDGE_ENABLED` | `true` | 设为 false 时禁用知识检索及题库来源索引，MySQL 题库仍可用 |

内容修订规则：

1. 稳定题目 ID 保持不变；任何题目或绑定快照变化必须增加 `version`。同 ID、同版本发生变化会报冲突。
2. 评分要点、绑定知识点或来源变化时，同时增加该题的 `rubricVersion`。只增加题目版本不能绕过评分资料的同版本冲突校验。
3. 来源更新需增加来源 `version` 并使用新的知识文档 ID，例如 `p1.redis.lock.v2`；原文档和快照保留。
4. 当前题目指向最高已导入版本。再次导入旧题库不会降级当前题目；历史快照包含当时的题干、知识点、评分资料和来源原文。

`documentRevision` 是规范序列化后的完整来源定义的 SHA-256，包含版本、内容、元数据和核对日期。它是评估快照的来源指纹，与 Chroma 内部为分块、模型及索引配置计算的版本不同。两者通过不可复用的 `documentId` 对应，不能直接混用指纹。

`p1.*` 是保留命名空间。通用知识导入和删除接口拒绝该前缀，避免题目绑定的资料被手动覆盖。后续评分以数据库中的题目版本快照为依据，再根据服务端实际提供的参考资料校验引用。单实例导入有串行协调；多副本写入协调不属于本次交付。

## 题库接口验收

```text
GET /api/questions
GET /api/questions?topic=REDIS&difficulty=2&page=0&size=5
GET /api/questions/redis.lua-stock
GET /api/questions/redis.lua-stock?version=1
```

主题枚举为 `REDIS`、`MYSQL`、`SPRING`、`KAFKA`、`JAVA_CONCURRENCY`、`JVM`；难度为 1–5，page 为 0–10000，size 为 1–50，默认 page=0、size=12。

分页响应含 `items`、`page`、`size`、`totalElements`、`totalPages`。每题只返回 `id`、`version`、`topic`、`difficulty`、`title`、`prompt`、`suggestedSeconds` 七个字段，不返回解析、评分要点或来源原文。此限制针对正常题目接口；现有独立知识查询接口继续保留学习资料。

非法参数返回 400，题目或指定版本不存在返回 404。合法筛选无结果或超出最后一页返回空列表。

实测：共 24 题；Redis 4 题，其中难度 2 有 2 题；每页 5 题得到 5 页，相邻页无重复；JVM 难度 1 筛选为空。未知主题、越界难度、负页码、过大页长、非数字页长、未知题目、零版本和不存在版本共 8 种请求均符合预期。

## 来源核对

以下官方页面在 2026-10-02 核对。来源文本是项目内整理的知识摘要，不是从网页自动抓取的完整文章。范围以 Java 17、MySQL 8.0、Kafka 4.1 及对应 Redis 基础机制为主；Spring 默认异常回滚和传播语义使用 6.1.14 API，代理调用机制参考官方说明。升级依赖或修改条件后应重新核对并修订版本。

每条来源绑定题内三个要点。除了机制本身，也核对其适用条件；跨数据库一致性、异步事务边界和消费副作用不会被描述为自动获得的保证。

| 题目 ID | 主题 | 官方依据 | 已核对要点 ID |
| --- | --- | --- | --- |
| `redis.lua-stock` | Redis | [Lua 与库存超卖](https://redis.io/docs/latest/develop/programmability/eval-intro/) | `race`、`scope`、`bound` |
| `redis.lock` | Redis | [锁的所有权与过期](https://redis.io/docs/latest/develop/clients/patterns/distributed-locks/) | `acquire`、`release`、`expiry` |
| `redis.eviction` | Redis | [内存淘汰策略](https://redis.io/docs/latest/develop/reference/eviction/) | `policy`、`access`、`reject` |
| `redis.persistence` | Redis | [RDB 与 AOF](https://redis.io/docs/latest/operate/oss_and_stack/management/persistence/) | `rdb`、`aof`、`cost` |
| `mysql.explain` | MySQL | [慢查询与执行计划](https://dev.mysql.com/doc/refman/8.0/en/explain.html) | `plan`、`actual`、`context` |
| `mysql.composite-index` | MySQL | [联合索引与左前缀](https://dev.mysql.com/doc/refman/8.0/en/multiple-column-indexes.html) | `prefix`、`gap`、`cost` |
| `mysql.isolation` | MySQL | [事务隔离与快照](https://dev.mysql.com/doc/refman/8.0/en/innodb-transaction-isolation-levels.html) | `default`、`rc`、`rr` |
| `mysql.mvcc-locking` | MySQL | [快照读取与锁定读取](https://dev.mysql.com/doc/refman/8.0/en/innodb-consistent-read.html) | `snapshot`、`locking`、`own` |
| `spring.proxy` | Spring | [事务代理与自调用](https://docs.spring.io/spring-framework/reference/data-access/transaction/declarative/annotations.html) | `proxy`、`self`、`fix` |
| `spring.rollback` | Spring | [异常与事务回滚](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/transaction/annotation/Transactional.html) | `default`、`rule`、`boundary` |
| `spring.propagation` | Spring | [事务传播与独立资源](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/transaction/annotation/Propagation.html) | `required`、`new`、`resources` |
| `spring.async` | Spring | [异步任务与事务边界](https://docs.spring.io/spring-framework/docs/6.1.14/javadoc-api/org/springframework/transaction/annotation/Transactional.html) | `thread`、`async`、`rollback` |
| `kafka.producer` | Kafka | [Kafka 生产可靠性](https://kafka.apache.org/41/configuration/producer-configs/) | `acks`、`retry`、`idempotence` |
| `kafka.duplicate-consume` | Kafka | [重复消费与业务幂等](https://kafka.apache.org/41/design/design/) | `window`、`idempotent`、`scope` |
| `kafka.offset` | Kafka | [消费位置提交时机](https://kafka.apache.org/41/javadoc/org/apache/kafka/clients/consumer/KafkaConsumer.html) | `early`、`late`、`next` |
| `kafka.order` | Kafka | [分区与消息顺序](https://kafka.apache.org/41/design/design/) | `partition`、`key`、`processing` |
| `java.pool-sizing` | Java 线程池 | [线程数与队列顺序](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html) | `core`、`queue`、`reject` |
| `java.pool-unbounded` | Java 线程池 | [无界队列的风险](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html) | `growth`、`memory`、`bound` |
| `java.pool-rejection` | Java 线程池 | [拒绝策略的业务含义](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ThreadPoolExecutor.html) | `abort`、`caller`、`discard` |
| `java.pool-shutdown` | Java 线程池 | [关闭与任务取消](https://docs.oracle.com/en/java/javase/17/docs/api/java.base/java/util/concurrent/ExecutorService.html) | `graceful`、`interrupt`、`await` |
| `jvm.gc` | JVM | [GC 停顿定位](https://docs.oracle.com/en/java/javase/17/gctuning/) | `observe`、`goal`、`cause` |
| `jvm.memory` | JVM | [堆与其他内存](https://docs.oracle.com/en/java/javase/17/troubleshoot/troubleshooting-memory-leaks.html) | `regions`、`oom`、`native` |
| `jvm.leak` | JVM | [内存泄漏与引用链](https://docs.oracle.com/en/java/javase/17/troubleshoot/troubleshooting-memory-leaks.html) | `trend`、`reference`、`fix` |
| `jvm.heap-dump` | JVM | [堆转储采集边界](https://docs.oracle.com/en/java/javase/17/docs/specs/man/jcmd.html) | `command`、`impact`、`content` |

自动化测试验证所有来源与知识点引用均存在、主题匹配、版本稳定及知识索引映射；这些检查不代替事实审校，也不证明实际模型能正确理解资料。

## 固定样例与输出校验

| 样例类别 | 数量 | 检查重点 |
| --- | --- | --- |
| `COLLOQUIAL_CORRECT` | 4 | 不使用统一术语仍能表达正确机制 |
| `MISSING_POINTS` | 4 | 只覆盖部分内容，其余要点标为缺失 |
| `FACTUAL_ERROR` | 4 | 引用具体错误原话，保留纠正依据 |
| `UNRELATED` | 4 | 无关回答不能因存在技术词汇获得覆盖标签 |
| `WRONG_TERMINOLOGY` | 4 | 错误术语或概念替换有明确预期判定 |
| `EVIDENCE_INSUFFICIENT` | 4 | 无可用来源时允许全部 `UNCERTAIN` |

样例由项目内编写并带有预期标签、原话和原因，每题对应一条；不含模型真实预测。解析测试根据预期结果构造合法输出，并逐项检查契约。后续 P2 必须将实际模型输出与这些标签比较，不能将本次样例通过率宣传为评分准确率。

`TrainingEvaluationParser` 要求题目版本、评分版本和回答 SHA-256 匹配；要点不重复、不遗漏、不未知。JSON 不允许多余字段、尾随内容、重复字段、数字枚举及错误类型强制转换。

候选人引用及表达反馈引用必须是原回答的真实子串；`MISSING` 不带覆盖原话，`COVERED`、`PARTIAL`、`INCORRECT` 必须有原话。明确知识判定必须有依据，`UNCERTAIN` 可没有依据。来源必须属于当前要点、版本指纹匹配且引用原文真实存在。

`availableSourceIds` 必须由服务端根据实际送入评估的来源快照构建，不能从模型输出读取。解析器拒绝未提供、虚构、过期、重复或不匹配的引用；校验失败抛出异常，没有兜底分数。P1 没有评估保存接口，后续只能将成功解析的结果作为有效评估保存。来源和原话真实性校验不等于语义正确性校验。

## 工程与真实服务验收

| 验证 | 实测结果 |
| --- | --- |
| 完整后端测试 | 108 项，0 失败、0 错误、0 跳过，包含原有回归 |
| 评估解析测试 | 49 项：24 条固定样例、24 种非法输出、合法部分覆盖与不确定状态 |
| MySQL 8.0 | 真实事务提交；重复导入数量稳定；追加版本保留旧快照；旧导入不降级；冲突整批回滚 |
| ChromaDB 1.4.4 | 真实本地 BGE 向量；24 篇题库知识重复导入不增块；来源 ID、链接及原文与题目快照一致 |
| 检索样本 | Redis 锁、MySQL 执行计划、线程池无界队列均命中对应题库来源，实测相似度约 0.671 / 0.809 / 0.628 |
| 打包与完整应用启动 | Maven 打包成功；真实 MySQL 与 ChromaDB 环境中应用启动并自动导入成功 |
| 实际 HTTP | 筛选、分页、详情七字段、空结果和 8 种异常请求通过；知识检索命中对应 MySQL 题库资料 |

完整应用的验收集合有 34 个块：10 篇原内置知识卡加 24 篇 P1 题库资料。查询执行计划时第一项为 `p1.mysql.explain.v1`，也存在其他主题的低分命中；相似度并非正确率，不能把三个检索样本视为全题库准确性评估。

本次在隔离的临时数据库与 ChromaDB 容器上验收，没有调用 DeepSeek。验收用应用和临时容器在结束后清理，其他项目服务保持原状。构建目录中的 `p1-full-tests.log`、Surefire 报告和 `p1-http-acceptance.json` 是本地验收输出，可随构建目录清理；本记录保留结论。

## 重复验证

```powershell
cd backend
mvn test
```

默认测试通过 H2 的 MySQL 模式覆盖持久化行为，不需要外部 MySQL、Redis、ChromaDB 或模型密钥；真实服务测试未设置环境变量时会跳过。本次 108 项包含显式开启的真实 MySQL、P1 Chroma 和原 RAG Chroma 测试。

如需重复真实集成验收，先准备独立测试服务，再设置：

```powershell
# 必须是可清空的专用测试数据库，不能指向项目日常或生产数据库。
$env:P1_MYSQL_TEST_URL = 'jdbc:mysql://127.0.0.1:13316/p1_acceptance?useUnicode=true&characterEncoding=utf8&allowPublicKeyRetrieval=true&useSSL=false'
$env:P1_MYSQL_TEST_USER = 'root'
$env:P1_MYSQL_TEST_PASSWORD = '<测试数据库密码>'
$env:P1_CHROMA_TEST_URL = 'http://127.0.0.1:18000'
$env:RAG_CHROMA_TEST_URL = 'http://127.0.0.1:18000'
mvn test
mvn -DskipTests package
```

MySQL 集成测试对五张训练表使用 `create-drop`，会创建和删除这些表，必须使用专门的空数据库。Chroma 集成测试使用随机测试集合并在结束后清理。上述端口只是本次验收使用的示例，不是应用默认配置。

## P1 验收与后续边界

策划书 P1 的六项验收标准全部通过。原模拟面试、已有状态机及前端布局未在本阶段调整。题库、依据快照和输出校验可供 P2 复用，尚没有实际训练会话、回答提交、模型评分或重答页面。

P2 接入评分时，需要固定题目版本、保存回答与参考快照、构建服务端可用来源集合，然后执行严格解析；实际模型表现、错误率和用户练习效果需要单独测量。本次到 P1 验收结束。
