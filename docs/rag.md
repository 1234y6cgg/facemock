# ChromaDB 知识检索

## 当前链路

文本 / Markdown → 标准化换行 → 分块（360 字符，重叠 48）→ 本地中文 BGE 向量（512 维）→ ChromaDB v2 upsert。

检索问题 → 同一模型编码 → cosine 最近邻 → 相似度阈值过滤 → 带来源的参考片段 → 面试 Agent。

向量模型 `bge-small-zh-v1.5-q` 随 Maven 依赖打包，通过 ONNX 在 Java 进程内运行。运行时不需要向量 API Key，也不需要单独下载模型服务。首次构建会下载模型和本地推理依赖；首次启用有模型初始化开销。禁用知识库时不加载模型。

普通追问、压力面、场景题与回答质量评估均接入检索。问题检索包含最近对话、当前项目技术栈、岗位与追问层级；回答评估使用最近对话和本次回答。查询限制 500 字符，传给 Agent 的参考总长默认不超过 2400 字符。参考提供 `[K1]` 标识、标题、来源、来源链接与相似度，提示词要求将片段作为参考资料处理。

内置 10 篇 Java 后端知识卡，覆盖 Redis、MySQL、Spring 事务、Kafka、线程池与 JVM，内容为项目内整理的摘要，附官方文档链接。这是可运行的起始语料；其他岗位需要导入对应材料。

## 启动与配置

```bash
docker compose up -d --build
```

后端启动后自动导入内置知识。若 ChromaDB 尚未就绪，记录错误并在下一次检索时重试。成功导入后不会每次检索都重新导入；再次启动会比对版本，跳过未变化文档的向量生成。

| 配置 | 默认值 | 作用 |
| --- | --- | --- |
| `CHROMA_BASE_URL` | `http://localhost:8000` | 后端直连地址；Compose 内为 `http://chromadb:8000` |
| `KNOWLEDGE_ENABLED` | `true` | 总开关 |
| `KNOWLEDGE_SEED_ON_STARTUP` | `true` | 自动导入内置语料；设为 false 可使用纯自定义知识库 |
| `KNOWLEDGE_COLLECTION` | `tech_knowledge_bge_zh_v1` | 独立知识集合 |
| `KNOWLEDGE_TOP_K` | `3` | 面试默认检索数（1–10） |
| `KNOWLEDGE_MIN_SCORE` | `0.45` | 最低相似度，定义为 `1 - cosine distance`（不是正确率或概率） |

`application.yml` 还提供分块大小、重叠、上下文上限及连接 / 读取超时。中文检索阈值应结合自己的语料和问题调整，不能保证所有不相关问题都会被过滤。

Compose 固定使用已测试的 Chroma 1.4.4 镜像摘要，持久化到新卷 `chroma_rag_data` 的 `/data`。旧 `chroma_data` 卷和旧 `tech_knowledge` 集合保留，不自动删除或迁移。旧占位向量和新 512 维向量不能混用；集合的模型标识、维度和 cosine 配置不匹配时会报错，请使用独立集合。

如果在主机上单独运行后端，需要让 ChromaDB 的 8000 端口对主机可访问；当前 Compose 仅提供容器网络内访问。可创建独立开发实例：

```bash
docker run -d --name mockinterview-chroma-dev -p 127.0.0.1:8000:8000 -v mockinterview-chroma-dev:/data chromadb/chroma@sha256:1e0b73a187a28757c572acba508c46f48c9e8b0acaf5c20e6d95cdedce1acdf6
```

## 检查检索

后端运行时访问状态和检索接口：

```bash
curl http://localhost:8080/api/knowledge/status
curl -G http://localhost:8080/api/knowledge/search --data-urlencode "query=秒杀活动怎样避免库存超卖" --data-urlencode "topK=3"
```

状态包含 `enabled`、`available`、集合、模型、维度与分块数量；连接故障时 `totalChunks` 为 null，不能把它当成空库。

检索响应为命中数组，每项包含 `documentId`、`title`、`content`、`source`、`sourceUrl`、`score`。没有超过阈值的结果返回 `[]`；Chroma 或模型故障返回 HTTP 503。面试内部遇到知识库故障则记录日志并继续面试，不伪造引用。

## 导入自己的笔记

保存 UTF-8 的 `knowledge-note.json`：

```json
{
  "id": "my-project.inventory",
  "title": "我的秒杀项目：库存补偿",
  "source": "项目设计文档 v1",
  "sourceUrl": "",
  "tags": ["Redis", "库存", "补偿"],
  "content": "# 库存补偿\n订单创建失败时，以订单 ID 作为幂等键恢复预扣库存，并记录补偿结果用于对账。"
}
```

```bash
curl -X POST http://localhost:8080/api/knowledge/documents -H "Content-Type: application/json" --data-binary @knowledge-note.json
curl -X DELETE http://localhost:8080/api/knowledge/documents/my-project.inventory
curl -X POST http://localhost:8080/api/knowledge/seed
```

`content` 接受纯文本或 Markdown，单篇最多 20000 字符。ID 使用字母 / 数字开头，允许字母、数字、点、下划线和短横线，最多 100 字符；标题、来源必填。导入不会自动访问 `sourceUrl`，它用于保留出处。

使用同一个 ID 更新文档。内容、元数据、分块配置或模型改变时创建新版本；写入成功后才清理该文档的旧版本，避免写入失败先删掉已有内容。重复导入相同版本不会增加分块，也不重复计算向量。写入和旧版本清理是两个请求，若清理失败会返回 503，重试相同导入即可完成清理。单个应用实例内串行处理导入；多副本部署前需补充跨实例的同文档写入协调。

`builtin.*` 为内置语料 ID，使用自己的 ID 前缀以避免启动时被内置版本覆盖。手动删除内置文档后，下次启动或调用 `/seed` 会恢复它；关闭自动导入可保留自定义语料。

`p1.*` 为版本化题库资料的保留 ID，通用知识导入和删除接口拒绝该前缀。24 篇题库资料由仓库题库资源维护；更新时增加来源版本并使用新文档 ID，保留原评分依据。是否启动导入与索引分别由 `QUESTIONS_SEED_ON_STARTUP`、`QUESTIONS_INDEX_KNOWLEDGE` 控制；只关闭内置知识卡导入不会关闭题库索引。规则与验收见 [P1 验收记录](p1-acceptance.md)。

## 验证

P2 单题训练在现有检索基础上增加版本化依据快照，只接受本题绑定且能核对内容的检索片段。知识库不可用或没有相关命中时，界面明确说明；有已核验的题目资料才允许有限评估，无可用依据的要点只能标为“无法判断”。不会把检索相似度转换为答案分数。真实模型固定集及检索故障验收见 [P2 验收记录](p2-acceptance.md)。

```bash
cd backend
mvn test
```

默认测试不需要 MySQL、Redis、Chroma 或 DeepSeek。真实 Chroma 集成测试需显式设置 `RAG_CHROMA_TEST_URL`，并使用专门的测试实例。测试创建随机集合，结束后删除该测试集合。

PowerShell 示例（先启动上述开发实例，或使用单独测试容器）：

```powershell
$env:RAG_CHROMA_TEST_URL = 'http://127.0.0.1:8000'
mvn test
```

集成测试覆盖真实中文语义检索、无关问题过滤、来源信息、512 维向量、重复导入、缩短文档后清理旧片段及删除文档。Agent 接入测试验证参考资料传到四种调用中；不调用收费模型，因此没有验证 DeepSeek 最终问题与评分的质量。

实现参考：[LangChain4j 本地向量模型](https://docs.langchain4j.dev/integrations/embedding-models/in-process/)、[中文 BGE 模型 API](https://docs.langchain4j.dev/apidocs/dev/langchain4j/model/embedding/onnx/bgesmallzhv15q/BgeSmallZhV15QuantizedEmbeddingModel.html)、[Chroma REST API](https://api.trychroma.com/docs/)。
