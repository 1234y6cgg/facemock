# Java 后端题库扩充

日期：2026-10-03。当前共 **180 道题、18 类主题、540 个评分知识点**。在原 24 题基础上新增 156 题，每题包含场景化提问、三个评分要点、等价表达、常见误区、参考解析、专项追问和来源链接，可直接使用已有文字/语音练习、重答和复习流程。

## 覆盖范围

| 分类 | 总题数 | 主要考点 |
|---|---:|---|
| Java 基础 | 12 | 值传递、对象相等、字符串、异常、泛型、代理、金额、初始化 |
| Java 集合 | 10 | List、HashMap、ConcurrentHashMap、迭代器、树、堆、集合视图 |
| Java IO | 6 | 编码、BIO/NIO、Buffer、资源关闭、大文件、零拷贝 |
| Java 并发 | 14 | 线程池、JMM、volatile、锁、AQS、CAS、ThreadLocal、死锁、异步组合 |
| JVM | 12 | 运行时内存、类加载、GC、引用、逃逸分析、Safepoint、排障 |
| Spring | 14 | IoC、生命周期、作用域、循环依赖、AOP、事务、Boot、MVC、校验 |
| MyBatis | 6 | 参数绑定、缓存、N+1、动态 SQL、Mapper 代理、事务 |
| MySQL | 14 | 索引、执行计划、MVCC、间隙锁、日志、死锁、复制、分页、Buffer Pool |
| Redis | 12 | 数据类型、过期淘汰、锁、缓存穿透/击穿/雪崩、一致性、复制、哨兵、集群 |
| Kafka | 8 | 分区、消费组、位移、ISR、重复消费、轮询、保留策略 |
| 消息队列 | 8 | 确认、消费应答、重试死信、Outbox、事务消息、顺序、积压 |
| 计算机网络 | 12 | TCP/UDP、连接、拆包、拥塞、HTTP、缓存、TLS、DNS、WebSocket/SSE |
| 操作系统 | 10 | 进程线程、上下文切换、虚拟内存、缺页、IPC、调度、epoll、文件描述符 |
| 数据结构与算法 | 8 | 复杂度、二分、排序、堆、搜索树、图、布隆过滤器、LRU |
| 分布式 | 10 | CAP、Raft、fencing、ID、2PC、Saga、一致性哈希、重试、发现、限流熔断 |
| 系统设计 | 8 | 短链接、秒杀、幂等、分片、多级缓存、上传、Feed、容量估算 |
| 安全 | 8 | SQL 注入、XSS、CSRF、密码、JWT、授权、SSRF、密钥 |
| 工程实践 | 8 | 负载、CPU、日志、Git、容器、数据卷、健康探针 |

覆盖 Java 后端面试的主要基础与工程主题，并不声称穷尽所有框架、版本细节或面试题。算法部分训练思路表达，不包含代码判题器。

## 资料方法与出处

参考 [小林 coding](https://xiaolincoding.com/) 和 [JavaGuide](https://www.javaguide.cn/java/) 的主题目录确定覆盖范围；没有批量抓取、复制其付费或免费文章。新增题干、解析、误区与评分标准均为项目原创整理。RAG 保存的是这些训练要点，来源链接用于进一步核对，不把原创要点冒充网站原文。

技术核对主要使用 [JLS 17](https://docs.oracle.com/javase/specs/jls/se17/html/index.html)、[Java 17 API](https://docs.oracle.com/en/java/javase/17/docs/api/)、[OpenJDK 源码](https://github.com/openjdk/jdk17u)、[MySQL 8.0](https://dev.mysql.com/doc/refman/8.0/en/)、[Redis](https://redis.io/docs/latest/)、[Spring](https://docs.spring.io/spring-framework/reference/)、[MyBatis](https://mybatis.org/mybatis-3/)、[Kafka](https://kafka.apache.org/41/design/design/)、[RabbitMQ](https://www.rabbitmq.com/docs/confirms)、[RFC](https://www.rfc-editor.org/)、[OSTEP](https://pages.cs.wisc.edu/~remzi/OSTEP/)、[Princeton Algorithms](https://algs4.cs.princeton.edu/cheatsheet/)、[Raft 论文](https://raft.github.io/raft.pdf)、[Dynamo 论文](https://www.allthingsdistributed.com/files/amazon-dynamo-sosp2007.pdf)、[OWASP](https://cheatsheetseries.owasp.org/)、[Google SRE](https://sre.google/sre-book/)、Docker、Git 和 Kubernetes 官方资料。逐题链接位于来源卡片。

语言基础以 Java 17、数据库以 MySQL 8.0 为主要语境；Spring、Kafka 等在线资料可能随版本更新，不能把某个实现细节当成跨版本保证。来源卡片的核对日期表示本轮整理时间，不表示已由外部专家人工审定。系统设计题接受满足条件的其他合理方案，评分点作为参考边界。

## 导入与兼容

`catalog-v1.json` 原样保留；`catalog-v2.json` 是追加资源，题目版本仍从 1 起。加载器分别验证，再合并验证，拒绝重复 ID、缺失评分点与来源。MySQL 幂等导入，Chroma 按稳定文档 ID 与内容修订幂等索引。原题、旧会话、旧评分快照不被改写。新增知识点初始为未练习，不把覆盖增加算成能力提升。

新增分类统计接口 `GET /api/questions/topics`；列表支持 `q` 标题字面子串搜索（忽略英文大小写，最长 80 字），可与主题、难度、分页组合。搜索不是答案检索；列表不泄露评分解析。

练习的向量检索查询使用标题、题干和回答（最多 500 字），标题为模糊题干补充技术上下文。检索结果仍需与本题不可变来源绑定；未命中时明确显示有限评估，不能把其他题的资料当成本题证据。

维护源为 `docs/question-expansion-authoring.txt`，离线编译：

```sh
python backend/src/test/scripts/build-question-expansion.py
```

修改已导入题目内容时必须新增题目和相关来源修订版本，不能只覆盖同版本文件。生成器用于本轮首次追加；后续修订应显式维护版本并通过冲突和快照测试。

## 验收边界

新增内容通过结构、导入兼容、搜索统计和既有训练流程回归检查，实际部署及 RAG 核验见 [验收记录](question-expansion-acceptance.md)。原 P2/P6 的 24 题固定集成绩仍只适用于原固定集；新增 156 题评分校准不再列为交付任务，本轮结构与流程验收不作为整体判定准确率依据。
