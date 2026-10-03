# 面经Mock · 多Agent技术面试仿真系统 — 设计文档

- 日期：2026-09-12
- 状态：已确认（待实施）
- 范围：完整架构设计 + P0 核心闭环优先实施（P1/P2 预留扩展点）

---

## 一、项目概述

「面经Mock」是一个能真正模拟资深技术面试官的面试仿真工具。不是抽题题库、不是零散问答，而是有节奏、有深度、会追问、能施压的完整面试体验。

**核心爽点**：对着自己的简历跑一遍，能被追问到答不上来的真实痛点，结束后明确知道自己哪里薄弱、该怎么补。

**整体气质**：专业、克制、有压迫感，像真实技术面一样直奔主题，不废话、不客套、不灌水。

**P0 核心闭环**：上传简历 → 开始面试 → 连环追问 → 复盘输出。

---

## 二、硬边界

### 必须有
- 完整的多 Agent 协作架构，不同角色 Agent 分工完成全流程面试
- 基于简历内容的逐层深度追问，而非通用题库抽题
- 五层递进式追问逻辑（背景→方案→细节→难点→权衡扩展）
- 面试结束后的完整复盘诊断与薄弱点分析
- 支持 PDF / Word 版简历上传与结构化提取
- DeepSeek 作为核心推理引擎，使用 deepseek-flash
- Docker Compose 一键编排全部依赖服务与应用，本地单命令启动
- 所有中间件与应用均容器化，消除环境差异，开箱即用
- UI 简约大方

### 绝对不要
- 用户注册登录系统
- 花里胡哨的动画和过度设计的 UI
- 纯题库抽题模式
- 生成虚假简历、编造项目经历的功能
- 广告、引导页、营销内容
- 用 Redis 做向量检索（Redis 只做缓存与会话性能优化）
- Kubernetes、Service Mesh 等重型部署方案
- 复杂的多集群、多环境配置逻辑
- 容器内运行多余后台进程（严格单容器单进程）

### 技术栈硬约束
| 能力 | 技术 |
|---|---|
| 后端框架 | Spring Boot |
| Agent 核心框架 | LangChain4j |
| 缓存/会话/性能优化 | Redis（仅会话状态、热点缓存、接口限流、性能加速） |
| 结构化数据存储 | MySQL |
| 向量知识库 | ChromaDB |
| 文档解析 | Apache Tika |
| 大模型 | DeepSeek |
| 容器化编排 | Docker + Docker Compose |

---

## 三、范围与优先级

### P0（本次实施）
1. 简历解析与结构化提取
2. 面试总控 Agent（状态机编排）
3. 技术追问 Agent（五层递进）
4. 复盘诊断 Agent
5. 会话记忆
6. 容器化交付

### P1（P0 跑通后迭代，架构已预留）
1. 压力面 Agent
2. 动态难度调整
3. 技术知识库（ChromaDB 灌数据 + 检索）
4. 长期记忆（历史面试与薄弱点重复考察）
5. 问题去重机制

### P2（最后补充）
1. 场景考察 Agent
2. 多岗位方向适配
3. 面试历史记录与完整回放
4. 单题回答质量实时点评

---

## 四、系统架构（五层）

```
接入层        React SPA + Spring Boot REST / SSE
              ├─ 简历上传接口
              ├─ 面试对话接口（SSE 流式）
              └─ 复盘报告接口

Agent 编排层  状态机编排 + 角色 Agent 生命周期管理
              ├─ 面试总控 Agent（状态机 · 节奏控制 · 状态同步）
              ├─ 技术追问 Agent（五层递进 · 项目深挖）
              ├─ 压力面 Agent（P1 · 施压质疑）
              └─ 复盘诊断 Agent（多维评分 · 薄弱点）

能力层        LLM 封装 · 工具管理 · 记忆管理 · 规则控制
              ├─ DeepSeek 流式封装（StreamingChatModel）
              ├─ 工具调用管理（知识库检索等）
              ├─ 记忆管理（会话记忆 / 长期记忆）
              └─ 规则控制（五层逻辑 · 去重 · 难度）

存储层        ├─ MySQL（简历 · 面试记录 · 报告）
              ├─ Redis（会话状态 · 缓存 · 限流）
              └─ ChromaDB（技术知识库向量）

基础设施层    ├─ Apache Tika（文档解析）
              ├─ DeepSeek（模型接入）
              └─ Docker Compose（编排 · volume · .env）
```

---

## 五、部署拓扑（Docker Compose）

- **服务**：`app`（Spring Boot）、`mysql`、`redis`、`chromadb`，共 4 个容器
- **前端**：React 静态资源通过多阶段构建打进 `app` 镜像，由 Spring Boot 托管（无独立前端容器，单容器单进程）
- **端口**：外部只暴露 `app:8080`，中间件端口不对宿主机暴露
- **配置**：模型密钥、模型名（默认 `deepseek-flash`）、端口、账号密码全部通过 `.env` 注入，不硬编码进镜像
- **持久化**：MySQL/Redis/ChromaDB 数据通过 volume 挂载到本地
- **启动顺序**：`app` 依赖中间件，compose 配 `depends_on` + 健康检查
- **JDK**：与 Spring Boot 版本匹配（Spring Boot 3.x → JDK 17）

---

## 六、数据模型

### MySQL（P0 四张表）

| 表 | 关键字段 | 说明 |
|---|---|---|
| `resume` | `id`, `filename`, `raw_text`, `parsed_json`, `status`, `created_at` | 简历原文 + 结构化结果；`status ∈ PARSING/PARSED/FAILED` |
| `interview_session` | `id`, `resume_id`, `status`, `stage`, `layer`, `current_project_idx`, `question_count`, `started_at`, `ended_at` | 面试会话 + 状态机快照（落库支持断点续面） |
| `interview_message` | `id`, `session_id`, `role`, `content`, `layer`, `question_idx`, `created_at` | 全程对话；`role ∈ interviewer/candidate/system` |
| `interview_report` | `id`, `session_id`, `scores_json`, `weaknesses_json`, `suggestions_json`, `created_at` | 复盘诊断结果 |

**`parsed_json` 结构**：
```json
{
  "summary": "候选人整体画像",
  "skills": ["Java", "MySQL", "Redis", "..."],
  "projects": [
    {
      "name": "项目名",
      "desc": "项目描述",
      "role": "个人职责",
      "tech_stack": ["..."],
      "responsibilities": ["..."],
      "highlights": ["难点/亮点"]
    }
  ]
}
```

### Redis 键（仅缓存/会话/限流，绝不碰向量）
| 键 | 用途 |
|---|---|
| `session:{id}:state` | 状态机热状态（阶段/层级/题号/难度/已问技术点） |
| `session:{id}:messages` | 近期对话缓存，加速上下文组装 |
| `ratelimit:{ip}` | 接口限流 |
| `resume:parse:{id}` | 解析结果缓存（幂等，重复上传不重复调 LLM） |

### ChromaDB（向量知识库）
- collection `tech_knowledge`：技术知识点、常见面试考点、方案对比库
- P0 建好 collection 结构，P1 才灌数据并启用检索

---

## 七、Agent 设计与状态机（核心）

### 角色职责分工

| Agent | 职责（内容层） | 输入 → 输出 |
|---|---|---|
| 总控 Agent（编排器） | 读状态机 → 决定下一步动作（问哪层/换角度/跳转/结束/触发施压） | 状态 + 评估结果 → 编排指令 |
| 技术追问 Agent | 按指令 + 简历项目 + 上下文生成问题；结构化评估回答 | 指令 + 上下文 → 问题 + 评估 |
| 压力面 Agent（P1） | 从方案合理性/极端场景/选型权衡施压质疑 | 触发信号 + 上下文 → 施压问题 |
| 复盘诊断 Agent | 汇总全程对话 → 多维评分、薄弱点清单、改进建议 | 完整对话 → 报告 JSON |

**关键分工原则**：总控决定「节奏」（确定性状态机），追问 Agent 负责「内容 + 评估」（LLM）。节奏可预测、可测试，同时保留 LLM 追问深度。

### 面试阶段（状态机宏观流转）

```
OPENING（开场）→ PROJECT_DIG（项目深挖，遍历简历项目）→ EXTENSION（扩展技术点验证）→ CLOSING（收尾）
```

### 五层递进（单个项目内）

```
L1 背景 → L2 方案 → L3 细节 → L4 难点 → L5 权衡扩展
```

### 追问节奏（每层分支逻辑）

| 情况 | 动作 |
|---|---|
| 回答有深度、准确、完整 | 进入下一层（L5 后进入下一项目） |
| 答错 / 含糊 | 同层换一个角度再验证一次 |
| 连续卡壳 2 次 | 自然跳转（换项目 / 换技术点），不纠缠 |
| 连续答对至 L5 | 触发压力面 Agent（P1） |

### 回答评估（结构化输出）

追问 Agent 对每次回答输出结构化评估：
```json
{ "depth": 0-5, "accuracy": 0-5, "completeness": 0-5, "stuck": false, "next_action": "DEEPER|REANGLE|JUMP|PRESSURE|END" }
```
状态机据此迁移。

### 状态机字段
`stage`（阶段）、`current_project_idx`（当前项目）、`layer`（当前层 L1-L5）、`question_count`（题数）、`difficulty`（难度，P1 动态）、`asked_topics`（已问技术点，P1 去重）、`consecutive_stuck`（连续卡壳计数）。

---

## 八、API 设计

| 方法 & 路径 | 请求 | 响应 | 说明 |
|---|---|---|---|
| `POST /api/resumes` | multipart 文件 | `{ resumeId }` | 上传，触发异步解析（Tika + DeepSeek） |
| `GET /api/resumes/{id}` | — | `{ status, parsedJson }` | 轮询直到 `PARSED` |
| `POST /api/interviews` | `{ resumeId }` | `{ sessionId }` | 创建会话，初始化状态机 |
| `POST /api/interviews/{id}/start` | — | SSE 流 | 流式输出开场问题 |
| `POST /api/interviews/{id}/answer` | `{ content }` | SSE 流 | 提交回答，流式输出下一轮 |
| `GET /api/interviews/{id}/messages` | — | 消息数组 | 刷新/断点恢复 |
| `GET /api/interviews/{id}/report` | — | 报告 JSON | 复盘报告 |

### SSE 事件协议
- `event: message` — 面试官输出片段（前端逐字渲染）
- `event: done` — 本轮输出结束
- `event: interview_end` — 面试结束，报告开始生成
- `event: report_ready` — 报告生成完毕

**关键点**：评估回答、状态机迁移是内部同步过程（不输出）；只有面试官「说话」才流式推送。

---

## 九、核心数据流

1. **上传解析（一次性）**：上传 PDF/Word → Tika 抽纯文本 → DeepSeek 结构化 → 存入 MySQL
2. **追问循环（反复）**：候选人输入回答 → 追问 Agent 评估（深度/准确/完整+卡壳）→ 状态机迁移（下一层/换角度/跳转/施压/结束）→ 追问 Agent 生成下一问 → SSE 流式输出
3. **收尾**：状态机判定结束 → 复盘 Agent 汇总全程 → 多维评分报告 + 薄弱点 + 改进建议

---

## 十、错误处理

| 场景 | 策略 |
|---|---|
| DeepSeek 调用失败（超时/限流/密钥错） | LangChain4j `RetryPolicy` 指数退避；最终失败返回友好错误，状态机已落库可断点恢复 |
| 简历文件损坏 / 格式不支持 | `status=FAILED`，前端提示重新上传 |
| 结构化 LLM 失败 | 降级提示重试，保留已存 `raw_text` |
| SSE 连接中断 | 前端提示重连，`GET /messages` 恢复；`answer` 以会话状态为幂等锚点 |
| 输入校验 / 限流 | 400 统一错误体 / Redis 限流 429 |
| 统一响应 | `{ code, message, data }`，`@RestControllerAdvice` 全局兜底 |

---

## 十一、测试策略

**核心原则**：隔离确定性与不确定性（LLM 输出不可确定）。

1. 状态机迁移逻辑（纯函数，重点测）——五层递进/换角度/跳转/结束/施压分支，100% 确定性单测
2. 简历结构化结果解析——mock LLM 返回 JSON → 断言 parsedJson 正确
3. Repository 集成测试——MySQL/Redis/ChromaDB 用 Testcontainers
4. Agent 编排端到端——mock DeepSeek AiService（固定返回），测「追问→评估→状态机迁移」全链路
5. Controller 契约测试——MockMvc + mock 服务层
6. LLM 输出质量不写自动化测试——靠人工体验 + prompt 迭代打磨

---

## 十二、项目目录结构

```
面经Mock/
├─ backend/                          # Spring Boot 应用
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/mockinterview/
│     │  ├─ config/                  # LangChain4j / CORS / 异步配置
│     │  ├─ controller/              # 接入层：ResumeController, InterviewController
│     │  ├─ service/                 # 业务编排：resume / interview / report
│     │  ├─ agent/                   # 编排层：orchestrator(状态机) / followup / pressure / diagnosis
│     │  ├─ capability/              # 能力层：llm / memory / tool / rule
│     │  ├─ repository/              # MySQL / Redis / ChromaDB 访问
│     │  ├─ domain/                  # 领域模型 + DTO
│     │  └─ infrastructure/          # tika / deepseek 封装
│     └─ test/java/                  # 对应测试
├─ frontend/                         # React + Vite + TypeScript
│  └─ src/
│     ├─ pages/                      # Upload / Interview / Report 三个页面
│     ├─ components/                 # ChatBubble / MessageInput 等
│     ├─ api/                        # fetch 封装 + SSE 读取
│     └─ stores/                     # 前端状态
├─ docker-compose.yml                # 编排（app + mysql + redis + chromadb）
├─ .env.example                      # 环境变量模板（密钥/端口）
└─ README.md                         # 一键启动说明
```

---

## 十三、实施里程碑

| 里程碑 | 内容 | 对应 P 级 |
|---|---|---|
| M1 | 简历上传 + Tika 解析 + DeepSeek 结构化（可独立验证） | P0 |
| M2 | 状态机 + 总控 Agent + 追问 Agent（mock LLM 跑通追问循环） | P0 |
| M3 | 复盘诊断 Agent + SSE 流式输出 + 会话记忆 | P0 |
| M4 | React 前端三页面 + 完整交互闭环 | P0 |
| M5 | Docker Compose 编排 + .env + 健康检查 + 一键启动 | P0 |
| M6+ | 压力面 / 动态难度 / 知识库 / 长期记忆 / 去重 | P1/P2 |
