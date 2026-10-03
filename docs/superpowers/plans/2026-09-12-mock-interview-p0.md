# 面经Mock P0 核心闭环 实施计划

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 跑通「上传简历 → 开始面试 → 连环追问 → 复盘输出」完整闭环，交付可一键启动的多 Agent 技术面试仿真系统。

**Architecture:** 五层架构 —— React SPA + Spring Boot 接入层；状态机编排 + 角色 Agent（追问/复盘）编排层；DeepSeek 流式封装能力层；MySQL/Redis/ChromaDB 存储层；Tika/Docker 基础设施层。总控用确定性状态机掌控节奏，追问 Agent 用 LangChain4j 负责内容生成与回答评估。

**Tech Stack:** Spring Boot 3.3.5 · JDK 17 · LangChain4j 0.36.2 · Apache Tika 2.9.2 · DeepSeek（OpenAI 兼容接口）· MySQL 8 · Redis 7 · ChromaDB · React 18 + Vite + TypeScript · Docker Compose

**约定：**
- 版本已固定（Spring Boot 3.3.5 / LangChain4j 0.36.2 / Tika 2.9.2）。若因环境差异需微调版本，仅改动 `pom.xml` 与 `package.json`，不影响其余任务。
- 所有代码用 `com.mockinterview` 作为根包名。
- 模型名默认 `deepseek-flash`，经 `.env` 注入 `DEEPSEEK_MODEL_NAME`。
- 本计划按「写失败测试 → 验证失败 → 最小实现 → 验证通过 → 提交」的 TDD 节奏编写；测试用 JUnit 5 + MockMvc + Testcontainers。

**前置：初始化 git 仓库**（见 Task 0，计划中所有「提交」步骤依赖它）。

---

## 文件结构总览

```
面经Mock/
├─ backend/
│  ├─ pom.xml
│  └─ src/
│     ├─ main/java/com/mockinterview/
│     │  ├─ MockInterviewApplication.java
│     │  ├─ config/
│     │  │  ├─ DeepSeekProperties.java
│     │  │  ├─ LangChain4jConfig.java
│     │  │  └─ WebConfig.java
│     │  ├─ controller/
│     │  │  ├─ ResumeController.java
│     │  │  └─ InterviewController.java
│     │  ├─ service/
│     │  │  ├─ resume/ResumeService.java
│     │  │  ├─ resume/ResumeParser.java
│     │  │  ├─ interview/InterviewService.java
│     │  │  └─ interview/InterviewOrchestrator.java
│     │  ├─ agent/
│     │  │  ├─ InterviewStateMachine.java
│     │  │  ├─ InterviewState.java
│     │  │  ├─ Stage.java
│     │  │  ├─ Layer.java
│     │  │  ├─ AnswerAssessment.java
│     │  │  ├─ FollowUpAgent.java
│     │  │  └─ DiagnosisAgent.java
│     │  ├─ capability/llm/JsonExtractor.java
│     │  ├─ repository/
│     │  │  ├─ ResumeRepository.java
│     │  │  ├─ InterviewSessionRepository.java
│     │  │  ├─ InterviewMessageRepository.java
│     │  │  └─ InterviewReportRepository.java
│     │  ├─ domain/
│     │  │  ├─ Resume.java
│     │  │  ├─ InterviewSession.java
│     │  │  ├─ InterviewMessage.java
│     │  │  ├─ InterviewReport.java
│     │  │  ├─ ResumeStructured.java
│     │  │  └─ dto/...
│     │  └─ infrastructure/
│     │     ├─ tika/TikaTextExtractor.java
│     │     └─ redis/SessionStateStore.java
│     └─ test/java/com/mockinterview/
│        ├─ agent/InterviewStateMachineTest.java
│        ├─ service/resume/ResumeParserTest.java
│        └─ ...
├─ frontend/
│  ├─ package.json
│  ├─ vite.config.ts
│  ├─ index.html
│  └─ src/
│     ├─ main.tsx
│     ├─ App.tsx
│     ├─ api/client.ts
│     ├─ api/sse.ts
│     ├─ pages/UploadPage.tsx
│     ├─ pages/InterviewPage.tsx
│     ├─ pages/ReportPage.tsx
│     └─ components/ChatBubble.tsx
├─ docker-compose.yml
├─ backend/Dockerfile
├─ .env.example
└─ README.md
```

---

### Task 0: 初始化项目与 git 仓库

**Files:**
- Create: `.gitignore`
- Create: `backend/.gitignore`

- [ ] **Step 1: 创建根 .gitignore**

```gitignore
# Java / Maven
target/
*.class
# Node
node_modules/
dist/
# IDE
.idea/
.vscode/
*.iml
# env & 本地数据
.env
*.log
# 数据卷
data/
.superpowers/
```

- [ ] **Step 2: 创建 backend/.gitignore**（与根目录相同，后端单独构建时也用得到）

```gitignore
target/
*.class
.idea/
*.iml
```

- [ ] **Step 3: 初始化仓库并首次提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git init
git add .gitignore backend/.gitignore
git commit -m "chore: init project with gitignore"
```

Expected: `git init` 输出 `Initialized empty Git repository`，提交成功。

---

### Task 1: 后端 Spring Boot 脚手架 + 健康检查

**Files:**
- Create: `backend/pom.xml`
- Create: `backend/src/main/resources/application.yml`
- Create: `backend/src/main/java/com/mockinterview/MockInterviewApplication.java`
- Create: `backend/src/main/java/com/mockinterview/controller/HealthController.java`
- Test: `backend/src/test/java/com/mockinterview/controller/HealthControllerTest.java`

- [ ] **Step 1: 写 pom.xml**

```xml
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <parent>
        <groupId>org.springframework.boot</groupId>
        <artifactId>spring-boot-starter-parent</artifactId>
        <version>3.3.5</version>
        <relativePath/>
    </parent>

    <groupId>com.mockinterview</groupId>
    <artifactId>mock-interview-backend</artifactId>
    <version>0.1.0</version>
    <name>mock-interview-backend</name>

    <properties>
        <java.version>17</java.version>
        <langchain4j.version>0.36.2</langchain4j.version>
        <tika.version>2.9.2</tika.version>
    </properties>

    <dependencies>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-web</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-jpa</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-data-redis</artifactId>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-validation</artifactId>
        </dependency>

        <dependency>
            <groupId>com.mysql</groupId>
            <artifactId>mysql-connector-j</artifactId>
            <scope>runtime</scope>
        </dependency>

        <dependency>
            <groupId>org.projectlombok</groupId>
            <artifactId>lombok</artifactId>
            <optional>true</optional>
        </dependency>

        <!-- LangChain4j -->
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>
        <dependency>
            <groupId>dev.langchain4j</groupId>
            <artifactId>langchain4j-open-ai</artifactId>
            <version>${langchain4j.version}</version>
        </dependency>

        <!-- Apache Tika 文档解析 -->
        <dependency>
            <groupId>org.apache.tika</groupId>
            <artifactId>tika-parsers-standard-package</artifactId>
            <version>${tika.version}</version>
        </dependency>

        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-starter-test</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.springframework.boot</groupId>
            <artifactId>spring-boot-testcontainers</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>junit-jupiter</artifactId>
            <scope>test</scope>
        </dependency>
        <dependency>
            <groupId>org.testcontainers</groupId>
            <artifactId>mysql</artifactId>
            <scope>test</scope>
        </dependency>
    </dependencies>

    <build>
        <plugins>
            <plugin>
                <groupId>org.springframework.boot</groupId>
                <artifactId>spring-boot-maven-plugin</artifactId>
                <configuration>
                    <excludes>
                        <exclude>
                            <groupId>org.projectlombok</groupId>
                            <artifactId>lombok</artifactId>
                        </exclude>
                    </excludes>
                </configuration>
            </plugin>
        </plugins>
    </build>
</project>
```

- [ ] **Step 2: 写 application.yml**

```yaml
server:
  port: 8080

spring:
  datasource:
    url: jdbc:mysql://${MYSQL_HOST:localhost}:${MYSQL_PORT:3306}/${MYSQL_DATABASE:mockinterview}?useUnicode=true&characterEncoding=utf8&serverTimezone=UTC&allowPublicKeyRetrieval=true&useSSL=false
    username: ${MYSQL_USER:mock}
    password: ${MYSQL_PASSWORD:mock}
  jpa:
    hibernate:
      ddl-auto: update
    open-in-view: false
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

deepseek:
  api-key: ${DEEPSEEK_API_KEY:}
  base-url: ${DEEPSEEK_BASE_URL:https://api.deepseek.com}
  model-name: ${DEEPSEEK_MODEL_NAME:deepseek-flash}

chroma:
  base-url: ${CHROMA_BASE_URL:http://localhost:8000}
```

- [ ] **Step 3: 写主类**

```java
package com.mockinterview;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class MockInterviewApplication {
    public static void main(String[] args) {
        SpringApplication.run(MockInterviewApplication.class, args);
    }
}
```

- [ ] **Step 4: 写健康检查控制器**

```java
package com.mockinterview.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class HealthController {

    @GetMapping("/api/health")
    public Map<String, String> health() {
        return Map.of("status", "UP");
    }
}
```

- [ ] **Step 5: 写失败测试**

```java
package com.mockinterview.controller;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(HealthController.class)
class HealthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Test
    void healthReturnsUp() throws Exception {
        mvc.perform(get("/api/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"));
    }
}
```

- [ ] **Step 6: 运行测试并验证通过**

Run: `cd backend && mvn -q test -Dtest=HealthControllerTest`
Expected: BUILD SUCCESS，1 个测试通过。

> 首次运行会下载大量依赖，耗时较长；无需 docker 中的数据库，`@WebMvcTest` 只加载 web 层。

- [ ] **Step 7: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add backend/pom.xml backend/src
git commit -m "feat: spring boot scaffold with health check"
```

---

### Task 2: 前端 React + Vite 脚手架

**Files:**
- Create: `frontend/package.json`
- Create: `frontend/vite.config.ts`
- Create: `frontend/tsconfig.json`
- Create: `frontend/tsconfig.node.json`
- Create: `frontend/index.html`
- Create: `frontend/src/main.tsx`
- Create: `frontend/src/App.tsx`
- Create: `frontend/src/index.css`

- [ ] **Step 1: 写 package.json**

```json
{
  "name": "mock-interview-frontend",
  "private": true,
  "version": "0.1.0",
  "type": "module",
  "scripts": {
    "dev": "vite",
    "build": "tsc -b && vite build",
    "preview": "vite preview"
  },
  "dependencies": {
    "react": "^18.3.1",
    "react-dom": "^18.3.1",
    "react-router-dom": "^6.26.2",
    "react-markdown": "^9.0.1"
  },
  "devDependencies": {
    "@types/react": "^18.3.11",
    "@types/react-dom": "^18.3.1",
    "@vitejs/plugin-react": "^4.3.2",
    "typescript": "^5.6.2",
    "vite": "^5.4.8"
  }
}
```

- [ ] **Step 2: 写 vite.config.ts（含 dev 代理到后端）**

```typescript
import { defineConfig } from 'vite'
import react from '@vitejs/plugin-react'

export default defineConfig({
  plugins: [react()],
  server: {
    port: 5173,
    proxy: {
      '/api': {
        target: 'http://localhost:8080',
        changeOrigin: true,
      },
    },
  },
  build: {
    outDir: 'dist',
  },
})
```

- [ ] **Step 3: 写 tsconfig.json**

```json
{
  "compilerOptions": {
    "target": "ES2020",
    "useDefineForClassFields": true,
    "lib": ["ES2020", "DOM", "DOM.Iterable"],
    "module": "ESNext",
    "skipLibCheck": true,
    "moduleResolution": "bundler",
    "allowImportingTsExtensions": true,
    "resolveJsonModule": true,
    "isolatedModules": true,
    "noEmit": true,
    "jsx": "react-jsx",
    "strict": true,
    "noUnusedLocals": true,
    "noUnusedParameters": true,
    "noFallthroughCasesInSwitch": true
  },
  "include": ["src"],
  "references": [{ "path": "./tsconfig.node.json" }]
}
```

- [ ] **Step 4: 写 tsconfig.node.json**

```json
{
  "compilerOptions": {
    "composite": true,
    "skipLibCheck": true,
    "module": "ESNext",
    "moduleResolution": "bundler",
    "allowSyntheticDefaultImports": true,
    "strict": true
  },
  "include": ["vite.config.ts"]
}
```

- [ ] **Step 5: 写 index.html**

```html
<!doctype html>
<html lang="zh-CN">
  <head>
    <meta charset="UTF-8" />
    <meta name="viewport" content="width=device-width, initial-scale=1.0" />
    <title>面经Mock</title>
  </head>
  <body>
    <div id="root"></div>
    <script type="module" src="/src/main.tsx"></script>
  </body>
</html>
```

- [ ] **Step 6: 写 main.tsx**

```tsx
import React from 'react'
import ReactDOM from 'react-dom/client'
import App from './App'
import './index.css'

ReactDOM.createRoot(document.getElementById('root')!).render(
  <React.StrictMode>
    <App />
  </React.StrictMode>,
)
```

- [ ] **Step 7: 写 App.tsx（占位，后续任务替换为路由）**

```tsx
export default function App() {
  return (
    <main style={{ padding: 24, fontFamily: 'system-ui, sans-serif' }}>
      <h1>面经Mock</h1>
      <p>多 Agent 技术面试仿真系统</p>
    </main>
  )
}
```

- [ ] **Step 8: 写 index.css（极简重置 + 设计变量，保持克制）**

```css
:root {
  --bg: #fafafa;
  --surface: #ffffff;
  --border: #e5e7eb;
  --text: #111827;
  --text-dim: #6b7280;
  --accent: #312e81;
  --accent-soft: #eef2ff;
  --danger: #dc2626;
  --ok: #059669;
}

* { box-sizing: border-box; }
html, body, #root { height: 100%; margin: 0; }
body {
  background: var(--bg);
  color: var(--text);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC",
    "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
}
button { cursor: pointer; }
```

- [ ] **Step 9: 构建验证**

Run: `cd frontend && npm install && npm run build`
Expected: 输出 `dist/` 目录，无 TypeScript 报错。

- [ ] **Step 10: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add frontend
git commit -m "feat: react vite scaffold"
```

---

## 阶段一（M1）：简历解析与结构化提取

### Task 3: 领域模型 + 仓储

**Files:**
- Create: `backend/src/main/java/com/mockinterview/domain/ResumeStatus.java`
- Create: `backend/src/main/java/com/mockinterview/domain/InterviewStatus.java`
- Create: `backend/src/main/java/com/mockinterview/domain/MessageRole.java`
- Create: `backend/src/main/java/com/mockinterview/domain/ResumeStructured.java`
- Create: `backend/src/main/java/com/mockinterview/domain/Resume.java`
- Create: `backend/src/main/java/com/mockinterview/domain/InterviewSession.java`
- Create: `backend/src/main/java/com/mockinterview/domain/InterviewMessage.java`
- Create: `backend/src/main/java/com/mockinterview/domain/InterviewReport.java`
- Create: `backend/src/main/java/com/mockinterview/repository/ResumeRepository.java`
- Create: `backend/src/main/java/com/mockinterview/repository/InterviewSessionRepository.java`
- Create: `backend/src/main/java/com/mockinterview/repository/InterviewMessageRepository.java`
- Create: `backend/src/main/java/com/mockinterview/repository/InterviewReportRepository.java`
- Test: `backend/src/test/java/com/mockinterview/domain/ResumeStructuredTest.java`

- [ ] **Step 1: 写三个枚举**

```java
package com.mockinterview.domain;

public enum ResumeStatus { PARSING, PARSED, FAILED }
```

```java
package com.mockinterview.domain;

public enum InterviewStatus { IN_PROGRESS, COMPLETED, ABORTED }
```

```java
package com.mockinterview.domain;

public enum MessageRole { INTERVIEWER, CANDIDATE, SYSTEM }
```

- [ ] **Step 2: 写 ResumeStructured（结构化简历 POJO，对应 parsedJson）**

```java
package com.mockinterview.domain;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResumeStructured {

    private String summary;
    private List<String> skills;
    private List<Project> projects;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Project {
        private String name;
        private String desc;
        private String role;
        private List<String> techStack;
        private List<String> responsibilities;
        private List<String> highlights;
    }
}
```

- [ ] **Step 3: 写 Resume 实体**

```java
package com.mockinterview.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "resume")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Resume {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String filename;

    @Lob
    @Column(name = "raw_text")
    private String rawText;

    @Lob
    @Column(name = "parsed_json")
    private String parsedJson;

    @Enumerated(EnumType.STRING)
    private ResumeStatus status;

    private LocalDateTime createdAt;
}
```

- [ ] **Step 4: 写 InterviewSession 实体（含状态机快照字段，落库支持断点续面）**

```java
package com.mockinterview.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_session")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "resume_id", nullable = false)
    private Long resumeId;

    @Enumerated(EnumType.STRING)
    private InterviewStatus status;

    private String stage;   // Stage 枚举名
    private String layer;   // Layer 枚举名

    @Column(name = "current_project_idx")
    private int currentProjectIdx;

    @Column(name = "total_projects")
    private int totalProjects;

    @Column(name = "question_count")
    private int questionCount;

    @Column(name = "consecutive_stuck")
    private int consecutiveStuck;

    private LocalDateTime startedAt;
    private LocalDateTime endedAt;
}
```

- [ ] **Step 5: 写 InterviewMessage 实体**

```java
package com.mockinterview.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_message")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewMessage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    @Enumerated(EnumType.STRING)
    private MessageRole role;

    @Lob
    private String content;

    private String layer;

    @Column(name = "question_idx")
    private Integer questionIdx;

    private LocalDateTime createdAt;
}
```

- [ ] **Step 6: 写 InterviewReport 实体**

```java
package com.mockinterview.domain;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(name = "interview_report")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_id", nullable = false, unique = true)
    private Long sessionId;

    @Lob
    @Column(name = "scores_json")
    private String scoresJson;

    @Lob
    @Column(name = "weaknesses_json")
    private String weaknessesJson;

    @Lob
    @Column(name = "suggestions_json")
    private String suggestionsJson;

    private LocalDateTime createdAt;
}
```

- [ ] **Step 7: 写四个仓储接口（Spring Data JPA，薄接口，无需测试）**

```java
package com.mockinterview.repository;

import com.mockinterview.domain.Resume;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ResumeRepository extends JpaRepository<Resume, Long> {
}
```

```java
package com.mockinterview.repository;

import com.mockinterview.domain.InterviewSession;
import org.springframework.data.jpa.repository.JpaRepository;

public interface InterviewSessionRepository extends JpaRepository<InterviewSession, Long> {
}
```

```java
package com.mockinterview.repository;

import com.mockinterview.domain.InterviewMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface InterviewMessageRepository extends JpaRepository<InterviewMessage, Long> {
    List<InterviewMessage> findBySessionIdOrderByCreatedAtAsc(Long sessionId);
}
```

```java
package com.mockinterview.repository;

import com.mockinterview.domain.InterviewReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface InterviewReportRepository extends JpaRepository<InterviewReport, Long> {
    Optional<InterviewReport> findBySessionId(Long sessionId);
}
```

- [ ] **Step 8: 写失败测试（ResumeStructured 与 parsedJson 的 Jackson 往返）**

```java
package com.mockinterview.domain;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ResumeStructuredTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void roundTripsThroughJson() throws Exception {
        ResumeStructured rs = ResumeStructured.builder()
                .summary("后端工程师")
                .skills(List.of("Java", "MySQL"))
                .projects(List.of(ResumeStructured.Project.builder()
                        .name("订单系统")
                        .techStack(List.of("Spring Boot"))
                        .build()))
                .build();

        String json = mapper.writeValueAsString(rs);
        ResumeStructured back = mapper.readValue(json, ResumeStructured.class);

        assertEquals("后端工程师", back.getSummary());
        assertEquals("订单系统", back.getProjects().get(0).getName());
        assertEquals(List.of("Java", "MySQL"), back.getSkills());
    }
}
```

- [ ] **Step 9: 运行测试并验证通过**

Run: `cd backend && mvn -q test -Dtest=ResumeStructuredTest`
Expected: BUILD SUCCESS，1 个测试通过。

- [ ] **Step 10: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add backend/src
git commit -m "feat: domain entities and repositories"
```

---

### Task 4: LangChain4j / DeepSeek 配置

**Files:**
- Create: `backend/src/main/java/com/mockinterview/config/DeepSeekProperties.java`
- Create: `backend/src/main/java/com/mockinterview/config/LangChain4jConfig.java`
- Create: `backend/src/main/java/com/mockinterview/config/WebConfig.java`

> 本任务是基础设施胶水（无业务逻辑），不写单测，正确性由最终 Docker 冒烟测试（Task 19）验证。此步只做编译校验。

- [ ] **Step 1: 写 DeepSeekProperties**

```java
package com.mockinterview.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "deepseek")
public class DeepSeekProperties {
    private String apiKey = "";
    private String baseUrl = "https://api.deepseek.com";
    private String modelName = "deepseek-flash";
}
```

- [ ] **Step 2: 写 LangChain4jConfig（构建非流式 + 流式两个模型 bean）**

```java
package com.mockinterview.config;

import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiStreamingChatModel;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DeepSeekProperties.class)
public class LangChain4jConfig {

    @Bean
    public ChatLanguageModel chatLanguageModel(DeepSeekProperties p) {
        return OpenAiChatModel.builder()
                .baseUrl(p.getBaseUrl())
                .apiKey(p.getApiKey())
                .modelName(p.getModelName())
                .build();
    }

    @Bean
    public StreamingChatLanguageModel streamingChatLanguageModel(DeepSeekProperties p) {
        return OpenAiStreamingChatModel.builder()
                .baseUrl(p.getBaseUrl())
                .apiKey(p.getApiKey())
                .modelName(p.getModelName())
                .build();
    }
}
```

- [ ] **Step 3: 写 WebConfig（CORS，供本地 Vite 直连备选）**

```java
package com.mockinterview.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/api/**")
                .allowedOrigins("http://localhost:5173")
                .allowedMethods("GET", "POST", "OPTIONS");
    }
}
```

- [ ] **Step 4: 编译校验**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 5: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add backend/src/main/java/com/mockinterview/config
git commit -m "feat: langchain4j deepseek config"
```

---

### Task 5: Tika 文本抽取

**Files:**
- Create: `backend/src/main/java/com/mockinterview/infrastructure/tika/TikaTextExtractor.java`
- Test: `backend/src/test/java/com/mockinterview/infrastructure/tika/TikaTextExtractorTest.java`

- [ ] **Step 1: 写失败测试**

```java
package com.mockinterview.infrastructure.tika;

import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

class TikaTextExtractorTest {

    @Test
    void extractsTextFromStream() throws Exception {
        TikaTextExtractor extractor = new TikaTextExtractor();
        String out = extractor.extract(new ByteArrayInputStream(
                "Java 后端工程师，负责订单系统。".getBytes(StandardCharsets.UTF_8)));
        assertTrue(out.contains("订单系统"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd backend && mvn -q test -Dtest=TikaTextExtractorTest`
Expected: 编译失败（`TikaTextExtractor` 未定义）。

- [ ] **Step 3: 写实现**

```java
package com.mockinterview.infrastructure.tika;

import org.apache.tika.Tika;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

@Component
public class TikaTextExtractor {

    private final Tika tika = new Tika();

    public String extract(InputStream in) throws IOException, TikaException {
        return tika.parseToString(in);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd backend && mvn -q test -Dtest=TikaTextExtractorTest`
Expected: BUILD SUCCESS。真实 PDF/DOCX 抽取在 Task 19 冒烟测试中验证。

- [ ] **Step 5: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add backend/src/main/java/com/mockinterview/infrastructure/tika backend/src/test/java/com/mockinterview/infrastructure/tika
git commit -m "feat: tika text extractor"
```

---

### Task 6: 简历结构化解析（DeepSeek 抽取 + JSON 提取）

**Files:**
- Create: `backend/src/main/java/com/mockinterview/capability/llm/JsonExtractor.java`
- Create: `backend/src/main/java/com/mockinterview/service/resume/ResumeStructuringModel.java`
- Create: `backend/src/main/java/com/mockinterview/infrastructure/deepseek/DeepSeekResumeStructuringModel.java`
- Create: `backend/src/main/java/com/mockinterview/service/resume/ResumeParser.java`
- Test: `backend/src/test/java/com/mockinterview/capability/llm/JsonExtractorTest.java`
- Test: `backend/src/test/java/com/mockinterview/service/resume/ResumeParserTest.java`

- [ ] **Step 1: 写 JsonExtractor 失败测试**

```java
package com.mockinterview.capability.llm;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class JsonExtractorTest {

    @Test
    void stripsMarkdownFence() {
        String in = "```json\n{\"summary\":\"x\"}\n```";
        assertEquals("{\"summary\":\"x\"}", JsonExtractor.extractJsonObject(in));
    }

    @Test
    void extractsEmbeddedJson() {
        String in = "好的，结果如下：{\"a\":1} 请查收";
        assertEquals("{\"a\":1}", JsonExtractor.extractJsonObject(in));
    }

    @Test
    void emptyWhenNoJson() {
        assertEquals("", JsonExtractor.extractJsonObject("没有 JSON"));
    }
}
```

- [ ] **Step 2: 运行测试验证失败**

Run: `cd backend && mvn -q test -Dtest=JsonExtractorTest`
Expected: 编译失败（`JsonExtractor` 未定义）。

- [ ] **Step 3: 写 JsonExtractor 实现**

```java
package com.mockinterview.capability.llm;

public final class JsonExtractor {

    private JsonExtractor() {
    }

    /** 从 LLM 输出中截取第一个 '{' 到最后一个 '}' 之间的 JSON 对象。 */
    public static String extractJsonObject(String text) {
        if (text == null) {
            return "";
        }
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end < start) {
            return "";
        }
        return text.substring(start, end + 1);
    }
}
```

- [ ] **Step 4: 运行测试验证通过**

Run: `cd backend && mvn -q test -Dtest=JsonExtractorTest`
Expected: BUILD SUCCESS，3 个测试通过。

- [ ] **Step 5: 写 ResumeStructuringModel 接口（抽象 LLM，便于 mock）**

```java
package com.mockinterview.service.resume;

public interface ResumeStructuringModel {
    String extractJson(String rawText);
}
```

- [ ] **Step 6: 写 DeepSeek 实现**

```java
package com.mockinterview.infrastructure.deepseek;

import com.mockinterview.service.resume.ResumeStructuringModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeepSeekResumeStructuringModel implements ResumeStructuringModel {

    private final ChatLanguageModel model;

    public DeepSeekResumeStructuringModel(ChatLanguageModel model) {
        this.model = model;
    }

    @Override
    public String extractJson(String rawText) {
        String prompt = """
                你是简历解析助手。从下面的简历纯文本中提取结构化信息，只输出一个 JSON 对象，不要输出任何其他文字、注释或代码块标记。
                字段要求：
                - summary: 一句话总结候选人背景
                - skills: 技术栈字符串数组
                - projects: 项目数组，每项含 name(项目名)、desc(描述)、role(个人职责)、techStack(技术栈数组)、responsibilities(职责数组)、highlights(难点/亮点数组)

                简历原文：
                %s
                """.formatted(rawText);
        return model.generate(List.of(UserMessage.from(prompt))).content().text();
    }
}
```

- [ ] **Step 7: 写 ResumeParser 失败测试（mock 模型）**

```java
package com.mockinterview.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.domain.ResumeStructured;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ResumeParserTest {

    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void parsesStructuredJson() {
        ResumeStructuringModel model = raw -> """
                {"summary":"后端工程师","skills":["Java","MySQL"],
                 "projects":[{"name":"订单系统","techStack":["Spring Boot"],
                              "responsibilities":["负责下单流程"]}]}
                """;
        ResumeParser parser = new ResumeParser(model, mapper);

        ResumeStructured r = parser.parse("任意简历文本");

        assertEquals("后端工程师", r.getSummary());
        assertEquals(1, r.getProjects().size());
        assertEquals("订单系统", r.getProjects().get(0).getName());
    }

    @Test
    void throwsWhenModelReturnsInvalidJson() {
        ResumeStructuringModel model = raw -> "这不是 JSON";
        ResumeParser parser = new ResumeParser(model, mapper);

        assertThrows(IllegalStateException.class, () -> parser.parse("文本"));
    }
}
```

- [ ] **Step 8: 运行测试验证失败**

Run: `cd backend && mvn -q test -Dtest=ResumeParserTest`
Expected: 编译失败（`ResumeParser` 未定义）。

- [ ] **Step 9: 写 ResumeParser 实现**

```java
package com.mockinterview.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.capability.llm.JsonExtractor;
import com.mockinterview.domain.ResumeStructured;
import org.springframework.stereotype.Component;

@Component
public class ResumeParser {

    private final ResumeStructuringModel model;
    private final ObjectMapper objectMapper;

    public ResumeParser(ResumeStructuringModel model, ObjectMapper objectMapper) {
        this.model = model;
        this.objectMapper = objectMapper;
    }

    public ResumeStructured parse(String rawText) {
        String raw = model.extractJson(rawText);
        String json = JsonExtractor.extractJsonObject(raw);
        try {
            return objectMapper.readValue(json, ResumeStructured.class);
        } catch (Exception e) {
            throw new IllegalStateException("简历结构化解析失败", e);
        }
    }
}
```

- [ ] **Step 10: 运行测试验证通过**

Run: `cd backend && mvn -q test -Dtest=ResumeParserTest,JsonExtractorTest`
Expected: BUILD SUCCESS。

- [ ] **Step 11: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add backend/src/main/java/com/mockinterview/capability backend/src/main/java/com/mockinterview/service/resume backend/src/main/java/com/mockinterview/infrastructure/deepseek backend/src/test/java/com/mockinterview/capability backend/src/test/java/com/mockinterview/service/resume
git commit -m "feat: resume structured extraction with deepseek"
```

---

### Task 7: 简历上传接口 + 异步解析服务

**Files:**
- Create: `backend/src/main/java/com/mockinterview/domain/dto/ResumeUploadResponse.java`
- Create: `backend/src/main/java/com/mockinterview/domain/dto/ResumeStatusResponse.java`
- Create: `backend/src/main/java/com/mockinterview/domain/dto/ApiError.java`
- Create: `backend/src/main/java/com/mockinterview/controller/NotFoundException.java`
- Create: `backend/src/main/java/com/mockinterview/controller/GlobalExceptionHandler.java`
- Create: `backend/src/main/java/com/mockinterview/config/AsyncConfig.java`
- Create: `backend/src/main/java/com/mockinterview/service/resume/ResumeParseWorker.java`
- Create: `backend/src/main/java/com/mockinterview/service/resume/ResumeService.java`
- Create: `backend/src/main/java/com/mockinterview/controller/ResumeController.java`
- Test: `backend/src/test/java/com/mockinterview/controller/ResumeControllerTest.java`

- [ ] **Step 1: 写 DTO 与异常**

```java
package com.mockinterview.domain.dto;

public record ResumeUploadResponse(Long resumeId) {
}
```

```java
package com.mockinterview.domain.dto;

import com.mockinterview.domain.ResumeStatus;
import com.mockinterview.domain.ResumeStructured;

public record ResumeStatusResponse(Long resumeId, ResumeStatus status, ResumeStructured parsed) {
}
```

```java
package com.mockinterview.domain.dto;

public record ApiError(int code, String message) {
}
```

```java
package com.mockinterview.controller;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String message) {
        super(message);
    }
}
```

- [ ] **Step 2: 写全局异常处理（统一 {code,message}）**

```java
package com.mockinterview.controller;

import com.mockinterview.domain.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(NotFoundException.class)
    public ResponseEntity<ApiError> notFound(NotFoundException e) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(new ApiError(404, e.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> generic(Exception e) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(new ApiError(500, e.getMessage()));
    }
}
```

- [ ] **Step 3: 写异步配置**

```java
package com.mockinterview.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;

@Configuration
@EnableAsync
public class AsyncConfig {
}
```

- [ ] **Step 4: 写异步解析 worker（在独立线程里调 DeepSeek，不阻塞上传请求）**

```java
package com.mockinterview.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.domain.Resume;
import com.mockinterview.domain.ResumeStatus;
import com.mockinterview.domain.ResumeStructured;
import com.mockinterview.repository.ResumeRepository;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ResumeParseWorker {

    private final ResumeRepository repository;
    private final ResumeParser parser;
    private final ObjectMapper objectMapper;

    public ResumeParseWorker(ResumeRepository repository, ResumeParser parser, ObjectMapper objectMapper) {
        this.repository = repository;
        this.parser = parser;
        this.objectMapper = objectMapper;
    }

    @Async
    public void parse(Long resumeId, String rawText) {
        repository.findById(resumeId).ifPresent(resume -> {
            try {
                ResumeStructured structured = parser.parse(rawText);
                resume.setParsedJson(objectMapper.writeValueAsString(structured));
                resume.setStatus(ResumeStatus.PARSED);
            } catch (Exception e) {
                resume.setStatus(ResumeStatus.FAILED);
            }
            repository.save(resume);
        });
    }
}
```

- [ ] **Step 5: 写 ResumeService**

```java
package com.mockinterview.service.resume;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.controller.NotFoundException;
import com.mockinterview.domain.Resume;
import com.mockinterview.domain.ResumeStatus;
import com.mockinterview.domain.ResumeStructured;
import com.mockinterview.domain.dto.ResumeStatusResponse;
import com.mockinterview.infrastructure.tika.TikaTextExtractor;
import com.mockinterview.repository.ResumeRepository;
import org.apache.tika.exception.TikaException;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;

@Service
public class ResumeService {

    private final ResumeRepository repository;
    private final TikaTextExtractor tika;
    private final ResumeParseWorker worker;
    private final ObjectMapper objectMapper;

    public ResumeService(ResumeRepository repository, TikaTextExtractor tika,
                         ResumeParseWorker worker, ObjectMapper objectMapper) {
        this.repository = repository;
        this.tika = tika;
        this.worker = worker;
        this.objectMapper = objectMapper;
    }

    public Long upload(MultipartFile file) {
        String rawText;
        try {
            rawText = tika.extract(file.getInputStream());
        } catch (IOException | TikaException e) {
            throw new IllegalStateException("简历文本抽取失败", e);
        }
        Resume resume = Resume.builder()
                .filename(file.getOriginalFilename())
                .rawText(rawText)
                .status(ResumeStatus.PARSING)
                .createdAt(LocalDateTime.now())
                .build();
        repository.save(resume);
        worker.parse(resume.getId(), rawText);
        return resume.getId();
    }

    public ResumeStatusResponse get(Long id) {
        Resume resume = repository.findById(id)
                .orElseThrow(() -> new NotFoundException("简历不存在: " + id));
        ResumeStructured parsed = null;
        if (resume.getStatus() == ResumeStatus.PARSED && resume.getParsedJson() != null) {
            try {
                parsed = objectMapper.readValue(resume.getParsedJson(), ResumeStructured.class);
            } catch (Exception ignored) {
                // 解析失败时返回 null，前端按 PARSING 处理
            }
        }
        return new ResumeStatusResponse(resume.getId(), resume.getStatus(), parsed);
    }
}
```

- [ ] **Step 6: 写 ResumeController**

```java
package com.mockinterview.controller;

import com.mockinterview.domain.dto.ResumeStatusResponse;
import com.mockinterview.domain.dto.ResumeUploadResponse;
import com.mockinterview.service.resume.ResumeService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/resumes")
public class ResumeController {

    private final ResumeService service;

    public ResumeController(ResumeService service) {
        this.service = service;
    }

    @PostMapping
    public ResumeUploadResponse upload(@RequestParam("file") MultipartFile file) {
        return new ResumeUploadResponse(service.upload(file));
    }

    @GetMapping("/{id}")
    public ResumeStatusResponse get(@PathVariable Long id) {
        return service.get(id);
    }
}
```

- [ ] **Step 7: 写失败测试（契约测试，mock 掉 Service）**

```java
package com.mockinterview.controller;

import com.mockinterview.domain.ResumeStatus;
import com.mockinterview.domain.dto.ResumeStatusResponse;
import com.mockinterview.service.resume.ResumeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ResumeController.class)
class ResumeControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private ResumeService service;

    @Test
    void uploadReturnsResumeId() throws Exception {
        when(service.upload(any())).thenReturn(42L);

        mvc.perform(multipart("/api/resumes")
                        .file(new MockMultipartFile("file", "resume.pdf",
                                "application/pdf", new byte[]{1, 2, 3})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resumeId").value(42));
    }

    @Test
    void getReturnsStatus() throws Exception {
        when(service.get(42L))
                .thenReturn(new ResumeStatusResponse(42L, ResumeStatus.PARSED, null));

        mvc.perform(get("/api/resumes/42"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PARSED"));
    }
}
```

- [ ] **Step 8: 运行测试验证通过**

Run: `cd backend && mvn -q test -Dtest=ResumeControllerTest`
Expected: BUILD SUCCESS，2 个测试通过。

- [ ] **Step 9: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add backend/src
git commit -m "feat: resume upload api with async parsing"
```

---

## 阶段二（M2）：状态机 + 总控 + 追问 Agent

### Task 8: 五层递进状态机（核心纯函数，重点 TDD）

**Files:**
- Create: `backend/src/main/java/com/mockinterview/agent/Stage.java`
- Create: `backend/src/main/java/com/mockinterview/agent/Layer.java`
- Create: `backend/src/main/java/com/mockinterview/agent/AnswerAssessment.java`
- Create: `backend/src/main/java/com/mockinterview/agent/InterviewState.java`
- Create: `backend/src/main/java/com/mockinterview/agent/InterviewStateMachine.java`
- Test: `backend/src/test/java/com/mockinterview/agent/InterviewStateMachineTest.java`

- [ ] **Step 1: 写 Stage 枚举（EXTENSION 为 P1/P2 预留，P0 不进入）**

```java
package com.mockinterview.agent;

public enum Stage { OPENING, PROJECT_DIG, EXTENSION, CLOSING }
```

- [ ] **Step 2: 写 Layer 枚举（五层递进）**

```java
package com.mockinterview.agent;

public enum Layer {
    L1_BACKGROUND("背景"),
    L2_SOLUTION("方案"),
    L3_DETAILS("细节"),
    L4_CHALLENGES("难点"),
    L5_TRADEOFF("权衡扩展");

    private final String label;

    Layer(String label) {
        this.label = label;
    }

    public String label() {
        return label;
    }

    public Layer next() {
        return switch (this) {
            case L1_BACKGROUND -> L2_SOLUTION;
            case L2_SOLUTION -> L3_DETAILS;
            case L3_DETAILS -> L4_CHALLENGES;
            case L4_CHALLENGES -> L5_TRADEOFF;
            case L5_TRADEOFF -> L5_TRADEOFF;
        };
    }

    public boolean isLast() {
        return this == L5_TRADEOFF;
    }
}
```

- [ ] **Step 3: 写 AnswerAssessment（回答评估结果）**

```java
package com.mockinterview.agent;

public record AnswerAssessment(int depth, int accuracy, int completeness, boolean stuck) {
}
```

- [ ] **Step 4: 写 InterviewState（不可变，builder 拷贝）**

```java
package com.mockinterview.agent;

import lombok.Builder;
import lombok.Value;

@Value
@Builder(toBuilder = true)
public class InterviewState {

    Stage stage;
    Layer layer;
    int currentProjectIdx;
    int totalProjects;
    int questionCount;
    int consecutiveStuck;

    public static InterviewState initial(int totalProjects) {
        return InterviewState.builder()
                .stage(Stage.OPENING)
                .layer(Layer.L1_BACKGROUND)
                .currentProjectIdx(0)
                .totalProjects(totalProjects)
                .questionCount(0)
                .consecutiveStuck(0)
                .build();
    }
}
```

- [ ] **Step 5: 写失败测试（先写状态机行为的 8 个测试）**

```java
package com.mockinterview.agent;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterviewStateMachineTest {

    private final InterviewStateMachine machine = new InterviewStateMachine();

    private static final AnswerAssessment GOOD = new AnswerAssessment(4, 4, 4, false);
    private static final AnswerAssessment STUCK = new AnswerAssessment(1, 1, 1, true);

    private static InterviewState state(Stage stage, Layer layer, int idx, int total, int q, int stuck) {
        return InterviewState.builder()
                .stage(stage).layer(layer)
                .currentProjectIdx(idx).totalProjects(total)
                .questionCount(q).consecutiveStuck(stuck)
                .build();
    }

    @Test
    void openingAnswerMovesToProjectDigAtL2() {
        InterviewState next = machine.apply(InterviewState.initial(2), GOOD);
        assertEquals(Stage.PROJECT_DIG, next.getStage());
        assertEquals(Layer.L2_SOLUTION, next.getLayer());
        assertEquals(1, next.getQuestionCount());
    }

    @Test
    void goodAnswerAdvancesLayer() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 1, 0);
        InterviewState next = machine.apply(s, GOOD);
        assertEquals(Layer.L3_DETAILS, next.getLayer());
        assertEquals(0, next.getConsecutiveStuck());
    }

    @Test
    void goodAnswerAtLastLayerJumpsToNextProject() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L5_TRADEOFF, 0, 2, 4, 0);
        InterviewState next = machine.apply(s, GOOD);
        assertEquals(1, next.getCurrentProjectIdx());
        assertEquals(Layer.L1_BACKGROUND, next.getLayer());
    }

    @Test
    void goodAnswerAtLastProjectCloses() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L5_TRADEOFF, 1, 2, 4, 0);
        InterviewState next = machine.apply(s, GOOD);
        assertEquals(Stage.CLOSING, next.getStage());
    }

    @Test
    void singleStuckStaysSameLayer() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 2, 0);
        InterviewState next = machine.apply(s, STUCK);
        assertEquals(Layer.L2_SOLUTION, next.getLayer());
        assertEquals(1, next.getConsecutiveStuck());
    }

    @Test
    void doubleStuckJumps() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 3, 1);
        InterviewState next = machine.apply(s, STUCK);
        assertEquals(1, next.getCurrentProjectIdx());
        assertEquals(Layer.L1_BACKGROUND, next.getLayer());
        assertEquals(0, next.getConsecutiveStuck());
    }

    @Test
    void goodAfterStuckResetsAndAdvances() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 3, 1);
        InterviewState next = machine.apply(s, GOOD);
        assertEquals(Layer.L3_DETAILS, next.getLayer());
        assertEquals(0, next.getConsecutiveStuck());
    }

    @Test
    void maxQuestionsCloses() {
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 7, 0);
        InterviewState next = machine.apply(s, GOOD);
        assertEquals(Stage.CLOSING, next.getStage());
    }
}
```

- [ ] **Step 6: 运行测试验证失败**

Run: `cd backend && mvn -q test -Dtest=InterviewStateMachineTest`
Expected: 编译失败（`InterviewStateMachine` 未定义）。

- [ ] **Step 7: 写 InterviewStateMachine 实现**

```java
package com.mockinterview.agent;

public class InterviewStateMachine {

    public static final int MAX_QUESTIONS = 8;
    public static final int MAX_STUCK = 2;

    public InterviewState apply(InterviewState state, AnswerAssessment assessment) {
        if (state.getStage() == Stage.CLOSING) {
            return state;
        }

        InterviewState advanced = state.toBuilder()
                .questionCount(state.getQuestionCount() + 1)
                .build();

        if (advanced.getQuestionCount() >= MAX_QUESTIONS) {
            return advanced.toBuilder().stage(Stage.CLOSING).build();
        }

        // 开场（L1 背景）已答完，进入项目深挖，从 L2 方案开始
        if (state.getStage() == Stage.OPENING) {
            return advanced.toBuilder()
                    .stage(Stage.PROJECT_DIG)
                    .layer(Layer.L2_SOLUTION)
                    .consecutiveStuck(0)
                    .build();
        }

        if (assessment.stuck()) {
            int stuck = state.getConsecutiveStuck() + 1;
            if (stuck >= MAX_STUCK) {
                return jump(advanced.toBuilder().consecutiveStuck(0).build());
            }
            return advanced.toBuilder().consecutiveStuck(stuck).build();
        }

        InterviewState reset = advanced.toBuilder().consecutiveStuck(0).build();
        if (!state.getLayer().isLast()) {
            return reset.toBuilder().layer(state.getLayer().next()).build();
        }
        return jump(reset);
    }

    private InterviewState jump(InterviewState s) {
        if (s.getCurrentProjectIdx() + 1 < s.getTotalProjects()) {
            return s.toBuilder()
                    .currentProjectIdx(s.getCurrentProjectIdx() + 1)
                    .layer(Layer.L1_BACKGROUND)
                    .build();
        }
        return s.toBuilder().stage(Stage.CLOSING).build();
    }
}
```

- [ ] **Step 8: 运行测试验证通过**

Run: `cd backend && mvn -q test -Dtest=InterviewStateMachineTest`
Expected: BUILD SUCCESS，8 个测试通过。

- [ ] **Step 9: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add backend/src/main/java/com/mockinterview/agent backend/src/test/java/com/mockinterview/agent
git commit -m "feat: five-layer interview state machine"
```

---

### Task 9: Agent 接口 + AiServices 装配 + 回答评估解析

**Files:**
- Create: `backend/src/main/java/com/mockinterview/agent/FollowUpAgent.java`
- Create: `backend/src/main/java/com/mockinterview/agent/AssessmentAgent.java`
- Create: `backend/src/main/java/com/mockinterview/config/AgentConfig.java`
- Create: `backend/src/main/java/com/mockinterview/agent/AssessmentParser.java`
- Test: `backend/src/test/java/com/mockinterview/agent/AssessmentParserTest.java`

- [ ] **Step 1: 写 FollowUpAgent（流式生成问题，历史显式传入）**

```java
package com.mockinterview.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface FollowUpAgent {

    @SystemMessage("""
            你是一名资深后端技术面试官，工作 5 年以上，专业、直接、不客套。
            你基于候选人的简历项目进行技术追问，严格遵循五层递进逻辑：背景→方案→细节→难点→权衡扩展。
            """)
    @UserMessage("""
            候选人简历概览：
            {{resume}}

            当前聚焦项目：
            {{project}}

            当前追问层级：{{layer}}

            面试对话历史：
            {{history}}

            请生成一个「{{layer}}」层级的追问问题。只输出问题本身，不要任何解释、前缀或引号。
            """)
    TokenStream generateQuestion(@V("resume") String resume,
                                 @V("project") String project,
                                 @V("layer") String layer,
                                 @V("history") String history);
}
```

- [ ] **Step 2: 写 AssessmentAgent（非流式，输出评估 JSON）**

```java
package com.mockinterview.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AssessmentAgent {

    @SystemMessage("""
            你是一名资深技术面试官，负责客观评估候选人的回答质量。
            """)
    @UserMessage("""
            候选人最后的回答：
            {{lastAnswer}}

            对话历史：
            {{history}}

            评估这次回答，只输出一个 JSON 对象，不要任何其他文字：
            {"depth":0到5的整数,"accuracy":0到5的整数,"completeness":0到5的整数,"stuck":true或false}
            其中 stuck 表示候选人明显卡壳、答不上来或答非所问。
            """)
    String assessAnswer(@V("history") String history, @V("lastAnswer") String lastAnswer);
}
```

- [ ] **Step 3: 写 AgentConfig（用 AiServices 装配两个 Agent，无共享记忆，历史由编排器显式传入）**

```java
package com.mockinterview.config;

import com.mockinterview.agent.AssessmentAgent;
import com.mockinterview.agent.FollowUpAgent;
import dev.langchain4j.model.chat.ChatLanguageModel;
import dev.langchain4j.model.chat.StreamingChatLanguageModel;
import dev.langchain4j.service.AiServices;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AgentConfig {

    @Bean
    public FollowUpAgent followUpAgent(StreamingChatLanguageModel streamingModel) {
        return AiServices.builder(FollowUpAgent.class)
                .streamingChatLanguageModel(streamingModel)
                .build();
    }

    @Bean
    public AssessmentAgent assessmentAgent(ChatLanguageModel model) {
        return AiServices.builder(AssessmentAgent.class)
                .chatLanguageModel(model)
                .build();
    }
}
```

- [ ] **Step 4: 写 AssessmentParser 失败测试**

```java
package com.mockinterview.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class AssessmentParserTest {

    private final AssessmentParser parser = new AssessmentParser(new ObjectMapper());

    @Test
    void parsesAssessment() {
        AnswerAssessment a = parser.parse(
                "```json\n{\"depth\":4,\"accuracy\":3,\"completeness\":4,\"stuck\":false}\n```");
        assertEquals(4, a.depth());
        assertEquals(3, a.accuracy());
        assertFalse(a.stuck());
    }

    @Test
    void defaultsOnInvalidJson() {
        AnswerAssessment a = parser.parse("这不是 JSON");
        assertEquals(0, a.depth());
        assertFalse(a.stuck());
    }
}
```

- [ ] **Step 5: 运行测试验证失败**

Run: `cd backend && mvn -q test -Dtest=AssessmentParserTest`
Expected: 编译失败（`AssessmentParser` 未定义）。

- [ ] **Step 6: 写 AssessmentParser 实现**

```java
package com.mockinterview.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.capability.llm.JsonExtractor;
import org.springframework.stereotype.Component;

@Component
public class AssessmentParser {

    private final ObjectMapper objectMapper;

    public AssessmentParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public AnswerAssessment parse(String json) {
        try {
            JsonNode node = objectMapper.readTree(JsonExtractor.extractJsonObject(json));
            return new AnswerAssessment(
                    node.path("depth").asInt(0),
                    node.path("accuracy").asInt(0),
                    node.path("completeness").asInt(0),
                    node.path("stuck").asBoolean(false));
        } catch (Exception e) {
            return new AnswerAssessment(0, 0, 0, false);
        }
    }
}
```

- [ ] **Step 7: 运行测试 + 全量编译校验**

Run: `cd backend && mvn -q test -Dtest=AssessmentParserTest && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 8: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add backend/src/main/java/com/mockinterview/agent backend/src/main/java/com/mockinterview/config/AgentConfig.java backend/src/test/java/com/mockinterview/agent
git commit -m "feat: follow-up and assessment agents"
```

---

### Task 10: Redis 会话状态存储

**Files:**
- Create: `backend/src/main/java/com/mockinterview/infrastructure/redis/SessionStateStore.java`

> 用 Redis 缓存面试状态快照（key `session:{id}:state`），MySQL 仍是事实来源。这是「会话状态」的 Redis 用法，不碰向量。转换逻辑内聚在本类，便于后续测试。

- [ ] **Step 1: 写 SessionStateStore**

```java
package com.mockinterview.infrastructure.redis;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.agent.InterviewState;
import com.mockinterview.agent.Layer;
import com.mockinterview.agent.Stage;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.Map;
import java.util.Optional;

@Component
public class SessionStateStore {

    private static final String KEY = "session:%d:state";
    private static final Duration TTL = Duration.ofHours(1);

    private final StringRedisTemplate redis;
    private final ObjectMapper objectMapper;

    public SessionStateStore(StringRedisTemplate redis, ObjectMapper objectMapper) {
        this.redis = redis;
        this.objectMapper = objectMapper;
    }

    public void saveState(Long sessionId, InterviewState state) {
        try {
            Map<String, Object> m = Map.of(
                    "stage", state.getStage().name(),
                    "layer", state.getLayer().name(),
                    "currentProjectIdx", state.getCurrentProjectIdx(),
                    "totalProjects", state.getTotalProjects(),
                    "questionCount", state.getQuestionCount(),
                    "consecutiveStuck", state.getConsecutiveStuck());
            redis.opsForValue().set(KEY.formatted(sessionId),
                    objectMapper.writeValueAsString(m), TTL);
        } catch (Exception ignored) {
            // Redis 不可用时降级为仅用 MySQL
        }
    }

    public Optional<InterviewState> loadState(Long sessionId) {
        String json = redis.opsForValue().get(KEY.formatted(sessionId));
        if (json == null) {
            return Optional.empty();
        }
        try {
            Map<String, Object> m = objectMapper.readValue(json, new TypeReference<>() {
            });
            return Optional.of(InterviewState.builder()
                    .stage(Stage.valueOf((String) m.get("stage")))
                    .layer(Layer.valueOf((String) m.get("layer")))
                    .currentProjectIdx(((Number) m.get("currentProjectIdx")).intValue())
                    .totalProjects(((Number) m.get("totalProjects")).intValue())
                    .questionCount(((Number) m.get("questionCount")).intValue())
                    .consecutiveStuck(((Number) m.get("consecutiveStuck")).intValue())
                    .build());
        } catch (Exception e) {
            return Optional.empty();
        }
    }
}
```

- [ ] **Step 2: 编译校验**

Run: `cd backend && mvn -q -DskipTests compile`
Expected: BUILD SUCCESS。

- [ ] **Step 3: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add backend/src/main/java/com/mockinterview/infrastructure/redis
git commit -m "feat: redis session state store"
```

---

### Task 11: 总控编排 + 面试服务 + SSE 接口

**Files:**
- Create: `backend/src/main/java/com/mockinterview/service/interview/InterviewOrchestrator.java`
- Create: `backend/src/main/java/com/mockinterview/domain/dto/CreateInterviewRequest.java`
- Create: `backend/src/main/java/com/mockinterview/domain/dto/CreateInterviewResponse.java`
- Create: `backend/src/main/java/com/mockinterview/domain/dto/AnswerRequest.java`
- Create: `backend/src/main/java/com/mockinterview/domain/dto/MessageDto.java`
- Create: `backend/src/main/java/com/mockinterview/service/interview/InterviewService.java`
- Create: `backend/src/main/java/com/mockinterview/controller/InterviewController.java`
- Test: `backend/src/test/java/com/mockinterview/service/interview/InterviewOrchestratorTest.java`
- Test: `backend/src/test/java/com/mockinterview/controller/InterviewControllerTest.java`

- [ ] **Step 1: 写 DTO**

```java
package com.mockinterview.domain.dto;

public record CreateInterviewRequest(Long resumeId) {
}
```

```java
package com.mockinterview.domain.dto;

public record CreateInterviewResponse(Long sessionId) {
}
```

```java
package com.mockinterview.domain.dto;

public record AnswerRequest(String content) {
}
```

```java
package com.mockinterview.domain.dto;

public record MessageDto(String role, String content, String layer) {
}
```

- [ ] **Step 2: 写 InterviewOrchestrator（总控：评估 → 状态机 → 生成问题）**

```java
package com.mockinterview.service.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.agent.*;
import com.mockinterview.domain.ResumeStructured;
import dev.langchain4j.service.TokenStream;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;

@Component
public class InterviewOrchestrator {

    private final InterviewStateMachine stateMachine;
    private final AssessmentAgent assessmentAgent;
    private final AssessmentParser assessmentParser;
    private final FollowUpAgent followUpAgent;
    private final ObjectMapper objectMapper;

    public InterviewOrchestrator(InterviewStateMachine stateMachine,
                                 AssessmentAgent assessmentAgent,
                                 AssessmentParser assessmentParser,
                                 FollowUpAgent followUpAgent,
                                 ObjectMapper objectMapper) {
        this.stateMachine = stateMachine;
        this.assessmentAgent = assessmentAgent;
        this.assessmentParser = assessmentParser;
        this.followUpAgent = followUpAgent;
        this.objectMapper = objectMapper;
    }

    public AnswerAssessment assess(String history, String lastAnswer) {
        String raw = assessmentAgent.assessAnswer(history, lastAnswer);
        return assessmentParser.parse(raw);
    }

    public InterviewState nextState(InterviewState state, AnswerAssessment assessment) {
        return stateMachine.apply(state, assessment);
    }

    public TokenStream generateQuestion(ResumeStructured resume, int projectIdx, Layer layer, String history) {
        return followUpAgent.generateQuestion(resumeContext(resume), projectContext(resume, projectIdx),
                layer.label(), history);
    }

    private String resumeContext(ResumeStructured resume) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "summary", resume.getSummary() == null ? "" : resume.getSummary(),
                    "skills", resume.getSkills() == null ? List.of() : resume.getSkills()));
        } catch (Exception e) {
            return "{}";
        }
    }

    private String projectContext(ResumeStructured resume, int idx) {
        if (resume.getProjects() == null || resume.getProjects().isEmpty()) {
            return "{}";
        }
        int i = Math.min(idx, resume.getProjects().size() - 1);
        try {
            return objectMapper.writeValueAsString(resume.getProjects().get(i));
        } catch (Exception e) {
            return "{}";
        }
    }
}
```

- [ ] **Step 3: 写 Orchestrator 失败测试（mock 掉两个 LLM Agent）**

```java
package com.mockinterview.service.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.agent.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class InterviewOrchestratorTest {

    private final InterviewStateMachine machine = new InterviewStateMachine();
    private final AssessmentParser parser = new AssessmentParser(new ObjectMapper());
    private final ObjectMapper mapper = new ObjectMapper();
    private final FollowUpAgent followUp = mock(FollowUpAgent.class);

    private final AssessmentAgent goodAgent = (history, answer) ->
            "{\"depth\":4,\"accuracy\":4,\"completeness\":4,\"stuck\":false}";
    private final AssessmentAgent stuckAgent = (history, answer) ->
            "{\"depth\":1,\"accuracy\":1,\"completeness\":1,\"stuck\":true}";

    private static InterviewState state(Stage stage, Layer layer, int idx, int total, int q, int stuck) {
        return InterviewState.builder()
                .stage(stage).layer(layer)
                .currentProjectIdx(idx).totalProjects(total)
                .questionCount(q).consecutiveStuck(stuck)
                .build();
    }

    @Test
    void goodAnswerAdvancesLayerThroughOrchestrator() {
        InterviewOrchestrator o = new InterviewOrchestrator(machine, goodAgent, parser, followUp, mapper);
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 1, 0);

        AnswerAssessment a = o.assess("历史", "我的回答");
        InterviewState next = o.nextState(s, a);

        assertEquals(Layer.L3_DETAILS, next.getLayer());
        assertEquals(0, next.getConsecutiveStuck());
    }

    @Test
    void stuckAnswerStaysSameLayer() {
        InterviewOrchestrator o = new InterviewOrchestrator(machine, stuckAgent, parser, followUp, mapper);
        InterviewState s = state(Stage.PROJECT_DIG, Layer.L2_SOLUTION, 0, 2, 2, 0);

        InterviewState next = o.nextState(s, o.assess("历史", "不会"));

        assertEquals(Layer.L2_SOLUTION, next.getLayer());
        assertEquals(1, next.getConsecutiveStuck());
    }
}
```

- [ ] **Step 4: 运行测试验证失败**

Run: `cd backend && mvn -q test -Dtest=InterviewOrchestratorTest`
Expected: 编译失败（`InterviewOrchestrator` 未定义）。

- [ ] **Step 5: 写 InterviewService（含 SSE 流式桥接）**

```java
package com.mockinterview.service.interview;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.agent.*;
import com.mockinterview.controller.NotFoundException;
import com.mockinterview.domain.*;
import com.mockinterview.domain.dto.MessageDto;
import com.mockinterview.infrastructure.redis.SessionStateStore;
import com.mockinterview.repository.*;
import dev.langchain4j.service.TokenStream;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class InterviewService {

    private final ResumeRepository resumeRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final InterviewOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final SessionStateStore stateStore;

    public InterviewService(ResumeRepository resumeRepository,
                            InterviewSessionRepository sessionRepository,
                            InterviewMessageRepository messageRepository,
                            InterviewOrchestrator orchestrator,
                            ObjectMapper objectMapper,
                            SessionStateStore stateStore) {
        this.resumeRepository = resumeRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.orchestrator = orchestrator;
        this.objectMapper = objectMapper;
        this.stateStore = stateStore;
    }

    public Long create(Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new NotFoundException("简历不存在: " + resumeId));
        if (resume.getStatus() != ResumeStatus.PARSED) {
            throw new IllegalStateException("简历尚未解析完成");
        }
        ResumeStructured structured = loadStructured(resume);
        int totalProjects = structured.getProjects() == null ? 0 : structured.getProjects().size();

        InterviewSession session = InterviewSession.builder()
                .resumeId(resumeId)
                .status(InterviewStatus.IN_PROGRESS)
                .stage(Stage.OPENING.name())
                .layer(Layer.L1_BACKGROUND.name())
                .currentProjectIdx(0)
                .totalProjects(totalProjects)
                .questionCount(0)
                .consecutiveStuck(0)
                .startedAt(LocalDateTime.now())
                .build();
        sessionRepository.save(session);
        return session.getId();
    }

    @Async
    public void start(Long sessionId, SseEmitter emitter) {
        try {
            InterviewSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new NotFoundException("会话不存在: " + sessionId));
            ResumeStructured resume = loadStructured(session.getResumeId());
            InterviewState state = loadState(session);
            String history = history(sessionId);
            TokenStream stream = orchestrator.generateQuestion(
                    resume, state.getCurrentProjectIdx(), state.getLayer(), history);
            streamQuestion(sessionId, stream, state.getLayer(), state.getQuestionCount(), emitter);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    @Async
    public void answer(Long sessionId, String content, SseEmitter emitter) {
        try {
            InterviewSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new NotFoundException("会话不存在: " + sessionId));
            InterviewState state = loadState(session);

            saveMessage(sessionId, MessageRole.CANDIDATE, content, state.getLayer().name(), state.getQuestionCount());
            String history = history(sessionId);

            AnswerAssessment assessment = orchestrator.assess(history, content);
            InterviewState next = orchestrator.nextState(state, assessment);
            saveState(session, next);

            if (next.getStage() == Stage.CLOSING) {
                emitter.send(SseEmitter.event().name("interview_end").data(""));
                emitter.complete();
                return;
            }

            ResumeStructured resume = loadStructured(session.getResumeId());
            TokenStream stream = orchestrator.generateQuestion(
                    resume, next.getCurrentProjectIdx(), next.getLayer(), history);
            streamQuestion(sessionId, stream, next.getLayer(), next.getQuestionCount(), emitter);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    public List<MessageDto> messages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(m -> new MessageDto(m.getRole().name(), m.getContent(), m.getLayer()))
                .toList();
    }

    // ---- 内部辅助 ----

    private void streamQuestion(Long sessionId, TokenStream stream, Layer layer, int questionIdx, SseEmitter emitter) {
        StringBuilder sb = new StringBuilder();
        stream.onNext(token -> {
                    sb.append(token);
                    try {
                        emitter.send(SseEmitter.event().name("message").data(token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onComplete(response -> {
                    String full = response.content() != null ? response.content().text() : sb.toString();
                    saveMessage(sessionId, MessageRole.INTERVIEWER, full, layer.name(), questionIdx);
                    try {
                        emitter.send(SseEmitter.event().name("done").data(""));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onError(emitter::completeWithError)
                .start();
    }

    private InterviewState loadState(InterviewSession session) {
        return stateStore.loadState(session.getId()).orElseGet(() -> InterviewState.builder()
                .stage(Stage.valueOf(session.getStage()))
                .layer(Layer.valueOf(session.getLayer()))
                .currentProjectIdx(session.getCurrentProjectIdx())
                .totalProjects(session.getTotalProjects())
                .questionCount(session.getQuestionCount())
                .consecutiveStuck(session.getConsecutiveStuck())
                .build());
    }

    private void saveState(InterviewSession session, InterviewState state) {
        session.setStage(state.getStage().name());
        session.setLayer(state.getLayer().name());
        session.setCurrentProjectIdx(state.getCurrentProjectIdx());
        session.setTotalProjects(state.getTotalProjects());
        session.setQuestionCount(state.getQuestionCount());
        session.setConsecutiveStuck(state.getConsecutiveStuck());
        if (state.getStage() == Stage.CLOSING) {
            session.setStatus(InterviewStatus.COMPLETED);
            session.setEndedAt(LocalDateTime.now());
        }
        sessionRepository.save(session);
        stateStore.saveState(session.getId(), state);
    }

    private String history(Long sessionId) {
        List<InterviewMessage> msgs = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        StringBuilder sb = new StringBuilder();
        for (InterviewMessage m : msgs) {
            sb.append(m.getRole().name()).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    private void saveMessage(Long sessionId, MessageRole role, String content, String layer, Integer questionIdx) {
        messageRepository.save(InterviewMessage.builder()
                .sessionId(sessionId).role(role).content(content)
                .layer(layer).questionIdx(questionIdx)
                .createdAt(LocalDateTime.now()).build());
    }

    private ResumeStructured loadStructured(InterviewSession session) {
        return loadStructured(session.getResumeId());
    }

    private ResumeStructured loadStructured(Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new NotFoundException("简历不存在: " + resumeId));
        try {
            return objectMapper.readValue(resume.getParsedJson(), ResumeStructured.class);
        } catch (Exception e) {
            throw new IllegalStateException("简历结构化数据不可用", e);
        }
    }
}
```

- [ ] **Step 6: 写 InterviewController**

```java
package com.mockinterview.controller;

import com.mockinterview.domain.dto.*;
import com.mockinterview.service.interview.InterviewService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.List;

@RestController
@RequestMapping("/api/interviews")
public class InterviewController {

    private final InterviewService service;

    public InterviewController(InterviewService service) {
        this.service = service;
    }

    @PostMapping
    public CreateInterviewResponse create(@RequestBody CreateInterviewRequest request) {
        return new CreateInterviewResponse(service.create(request.resumeId()));
    }

    @PostMapping("/{id}/start")
    public SseEmitter start(@PathVariable Long id) {
        SseEmitter emitter = new SseEmitter(5 * 60_000L);
        service.start(id, emitter);
        return emitter;
    }

    @PostMapping("/{id}/answer")
    public SseEmitter answer(@PathVariable Long id, @RequestBody AnswerRequest request) {
        SseEmitter emitter = new SseEmitter(5 * 60_000L);
        service.answer(id, request.content(), emitter);
        return emitter;
    }

    @GetMapping("/{id}/messages")
    public List<MessageDto> messages(@PathVariable Long id) {
        return service.messages(id);
    }
}
```

- [ ] **Step 7: 写 Controller 契约测试（mock Service，测 create 与 messages）**

```java
package com.mockinterview.controller;

import com.mockinterview.domain.dto.CreateInterviewRequest;
import com.mockinterview.domain.dto.MessageDto;
import com.mockinterview.service.interview.InterviewService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(InterviewController.class)
class InterviewControllerTest {

    @Autowired
    private MockMvc mvc;

    @MockBean
    private InterviewService service;

    @Test
    void createReturnsSessionId() throws Exception {
        when(service.create(any())).thenReturn(7L);

        mvc.perform(post("/api/interviews")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resumeId\":1}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value(7));
    }

    @Test
    void messagesReturnsList() throws Exception {
        when(service.messages(7L)).thenReturn(List.of(
                new MessageDto("INTERVIEWER", "你好", "L1_BACKGROUND"),
                new MessageDto("CANDIDATE", "你好", "L1_BACKGROUND")));

        mvc.perform(get("/api/interviews/7/messages"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].role").value("INTERVIEWER"))
                .andExpect(jsonPath("$[1].role").value("CANDIDATE"));
    }
}
```

- [ ] **Step 8: 运行测试验证通过**

Run: `cd backend && mvn -q test -Dtest=InterviewOrchestratorTest,InterviewControllerTest`
Expected: BUILD SUCCESS。SSE 流式端点由 Task 19 冒烟测试验证。

- [ ] **Step 9: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add backend/src
git commit -m "feat: interview orchestrator service and sse endpoints"
```

---

## 阶段三（M3）：复盘诊断 + 报告

### Task 12: 复盘诊断 Agent + 报告接口

**Files:**
- Create: `backend/src/main/java/com/mockinterview/agent/DiagnosisAgent.java`
- Create: `backend/src/main/java/com/mockinterview/agent/DiagnosisReport.java`
- Create: `backend/src/main/java/com/mockinterview/agent/DiagnosisParser.java`
- Create: `backend/src/main/java/com/mockinterview/domain/dto/ReportResponse.java`
- Modify: `backend/src/main/java/com/mockinterview/config/AgentConfig.java`（加 diagnosisAgent bean）
- Modify: `backend/src/main/java/com/mockinterview/service/interview/InterviewService.java`（加报告生成）
- Modify: `backend/src/main/java/com/mockinterview/controller/InterviewController.java`（加报告端点）
- Test: `backend/src/test/java/com/mockinterview/agent/DiagnosisParserTest.java`

- [ ] **Step 1: 写 DiagnosisAgent 接口**

```java
package com.mockinterview.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface DiagnosisAgent {

    @SystemMessage("""
            你是一名资深技术面试官，负责在面试结束后生成客观、专业的复盘诊断报告。
            """)
    @UserMessage("""
            简历概览：
            {{resume}}

            面试对话历史：
            {{history}}

            基于以上内容生成复盘诊断，只输出一个 JSON 对象，不要任何其他文字：
            {
              "scores": {"技术深度": 0到10整数, "表达准确性": 0到10整数, "项目理解": 0到10整数, "知识广度": 0到10整数, "总体": 0到10整数},
              "weaknesses": ["薄弱点1", "薄弱点2"],
              "suggestions": ["改进建议1", "改进建议2"]
            }
            weaknesses 必须具体、可执行，指出候选人答得最差的技术点。
            """)
    String generateReport(@V("resume") String resume, @V("history") String history);
}
```

- [ ] **Step 2: 写 DiagnosisReport 与 DiagnosisParser 失败测试**

```java
package com.mockinterview.agent;

import java.util.List;
import java.util.Map;

public record DiagnosisReport(Map<String, Object> scores, List<String> weaknesses, List<String> suggestions) {
}
```

```java
package com.mockinterview.agent;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DiagnosisParserTest {

    private final DiagnosisParser parser = new DiagnosisParser(new ObjectMapper());

    @Test
    void parsesReport() {
        DiagnosisReport r = parser.parse("""
                {"scores":{"技术深度":7,"总体":6},
                 "weaknesses":["Redis 主从理解浅"],
                 "suggestions":["深入学习分布式缓存"]}
                """);

        assertEquals(7, r.scores().get("技术深度"));
        assertEquals(1, r.weaknesses().size());
        assertEquals("Redis 主从理解浅", r.weaknesses().get(0));
    }

    @Test
    void defaultsOnInvalidJson() {
        DiagnosisReport r = parser.parse("garbage");
        assertTrue(r.scores().isEmpty());
        assertTrue(r.weaknesses().isEmpty());
    }
}
```

- [ ] **Step 3: 运行测试验证失败**

Run: `cd backend && mvn -q test -Dtest=DiagnosisParserTest`
Expected: 编译失败（`DiagnosisParser` 未定义）。

- [ ] **Step 4: 写 DiagnosisParser 实现**

```java
package com.mockinterview.agent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.capability.llm.JsonExtractor;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Component
public class DiagnosisParser {

    private final ObjectMapper objectMapper;

    public DiagnosisParser(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    public DiagnosisReport parse(String json) {
        try {
            JsonNode node = objectMapper.readTree(JsonExtractor.extractJsonObject(json));
            Map<String, Object> scores = objectMapper.convertValue(node.path("scores"),
                    new TypeReference<Map<String, Object>>() {
                    });
            List<String> weaknesses = new ArrayList<>();
            node.path("weaknesses").forEach(n -> weaknesses.add(n.asText()));
            List<String> suggestions = new ArrayList<>();
            node.path("suggestions").forEach(n -> suggestions.add(n.asText()));
            return new DiagnosisReport(scores, weaknesses, suggestions);
        } catch (Exception e) {
            return new DiagnosisReport(Map.of(), List.of(), List.of());
        }
    }
}
```

- [ ] **Step 5: 运行测试验证通过**

Run: `cd backend && mvn -q test -Dtest=DiagnosisParserTest`
Expected: BUILD SUCCESS，2 个测试通过。

- [ ] **Step 6: 写 ReportResponse DTO**

```java
package com.mockinterview.domain.dto;

import java.util.List;
import java.util.Map;

public record ReportResponse(Map<String, Object> scores, List<String> weaknesses, List<String> suggestions) {
}
```

- [ ] **Step 7: 在 AgentConfig 中新增 diagnosisAgent bean（新增 import 与 @Bean 方法）**

在 `AgentConfig.java` 顶部新增 import：
```java
import com.mockinterview.agent.DiagnosisAgent;
```

在类内新增方法：
```java
    @Bean
    public DiagnosisAgent diagnosisAgent(ChatLanguageModel model) {
        return AiServices.builder(DiagnosisAgent.class)
                .chatLanguageModel(model)
                .build();
    }
```

- [ ] **Step 8: 在 InterviewService 中新增报告生成**

在 `InterviewService.java` 顶部新增 import：
```java
import com.fasterxml.jackson.core.type.TypeReference;
import com.mockinterview.domain.dto.ReportResponse;
import java.util.Map;
import java.util.Optional;
```

新增三个字段（加在现有字段之后）：
```java
    private final DiagnosisAgent diagnosisAgent;
    private final DiagnosisParser diagnosisParser;
    private final InterviewReportRepository reportRepository;
```

将构造函数替换为（新增三个参数）：
```java
    public InterviewService(ResumeRepository resumeRepository,
                            InterviewSessionRepository sessionRepository,
                            InterviewMessageRepository messageRepository,
                            InterviewReportRepository reportRepository,
                            InterviewOrchestrator orchestrator,
                            DiagnosisAgent diagnosisAgent,
                            DiagnosisParser diagnosisParser,
                            ObjectMapper objectMapper,
                            SessionStateStore stateStore) {
        this.resumeRepository = resumeRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.reportRepository = reportRepository;
        this.orchestrator = orchestrator;
        this.diagnosisAgent = diagnosisAgent;
        this.diagnosisParser = diagnosisParser;
        this.objectMapper = objectMapper;
        this.stateStore = stateStore;
    }
```

在类内新增方法（放在 `messages` 方法之后）：
```java
    public ReportResponse report(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("会话不存在: " + sessionId));
        if (session.getStatus() != InterviewStatus.COMPLETED) {
            throw new IllegalStateException("面试尚未结束");
        }
        Optional<InterviewReport> existing = reportRepository.findBySessionId(sessionId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Resume resume = resumeRepository.findById(session.getResumeId())
                .orElseThrow(() -> new NotFoundException("简历不存在: " + session.getResumeId()));
        String raw = diagnosisAgent.generateReport(resume.getParsedJson(), history(sessionId));
        DiagnosisReport report = diagnosisParser.parse(raw);

        try {
            InterviewReport entity = InterviewReport.builder()
                    .sessionId(sessionId)
                    .scoresJson(objectMapper.writeValueAsString(report.scores()))
                    .weaknessesJson(objectMapper.writeValueAsString(report.weaknesses()))
                    .suggestionsJson(objectMapper.writeValueAsString(report.suggestions()))
                    .createdAt(LocalDateTime.now())
                    .build();
            reportRepository.save(entity);
        } catch (Exception ignored) {
        }
        return new ReportResponse(report.scores(), report.weaknesses(), report.suggestions());
    }

    private ReportResponse toResponse(InterviewReport r) {
        try {
            Map<String, Object> scores = objectMapper.readValue(r.getScoresJson(), new TypeReference<>() {
            });
            java.util.List<String> weaknesses = objectMapper.readValue(r.getWeaknessesJson(), new TypeReference<>() {
            });
            java.util.List<String> suggestions = objectMapper.readValue(r.getSuggestionsJson(), new TypeReference<>() {
            });
            return new ReportResponse(scores, weaknesses, suggestions);
        } catch (Exception e) {
            return new ReportResponse(Map.of(), java.util.List.of(), java.util.List.of());
        }
    }
```

- [ ] **Step 9: 在 InterviewController 中新增报告端点**

新增方法（放在 `messages` 之后）：
```java
    @GetMapping("/{id}/report")
    public ReportResponse report(@PathVariable Long id) {
        return service.report(id);
    }
```

- [ ] **Step 10: 编译 + 运行全部测试**

Run: `cd backend && mvn -q test`
Expected: BUILD SUCCESS，全部测试通过。

- [ ] **Step 11: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add backend/src
git commit -m "feat: diagnosis report generation and endpoint"
```

---

## 阶段四（M4）：React 前端

> 前端不写自动化单测（UI 行为由 Task 19 冒烟测试验证），每步用 `tsc` + `vite build` 做类型与构建校验。

### Task 13: API 客户端 + SSE 读取

**Files:**
- Create: `frontend/src/api/client.ts`
- Create: `frontend/src/api/sse.ts`

- [ ] **Step 1: 写 client.ts**

```typescript
const BASE = '/api'

export interface ResumeStructured {
  summary: string
  skills: string[]
  projects: {
    name: string
    desc: string
    role: string
    techStack: string[]
    responsibilities: string[]
    highlights: string[]
  }[]
}

export interface ResumeStatus {
  resumeId: number
  status: 'PARSING' | 'PARSED' | 'FAILED'
  parsed: ResumeStructured | null
}

export interface MessageDto {
  role: string
  content: string
  layer: string | null
}

export interface Report {
  scores: Record<string, number>
  weaknesses: string[]
  suggestions: string[]
}

export async function uploadResume(file: File): Promise<{ resumeId: number }> {
  const form = new FormData()
  form.append('file', file)
  const res = await fetch(`${BASE}/resumes`, { method: 'POST', body: form })
  if (!res.ok) throw new Error('上传失败')
  return res.json()
}

export async function getResumeStatus(id: number): Promise<ResumeStatus> {
  const res = await fetch(`${BASE}/resumes/${id}`)
  if (!res.ok) throw new Error('查询失败')
  return res.json()
}

export async function createInterview(resumeId: number): Promise<{ sessionId: number }> {
  const res = await fetch(`${BASE}/interviews`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ resumeId }),
  })
  if (!res.ok) throw new Error('创建面试失败')
  return res.json()
}

export async function getMessages(sessionId: number): Promise<MessageDto[]> {
  const res = await fetch(`${BASE}/interviews/${sessionId}/messages`)
  if (!res.ok) throw new Error('获取消息失败')
  return res.json()
}

export async function getReport(sessionId: number): Promise<Report> {
  const res = await fetch(`${BASE}/interviews/${sessionId}/report`)
  if (!res.ok) throw new Error('获取报告失败')
  return res.json()
}
```

- [ ] **Step 2: 写 sse.ts（POST 流式读取）**

```typescript
export interface SSEHandlers {
  onMessage: (token: string) => void
  onDone: () => void
  onInterviewEnd: () => void
}

export async function streamPost(url: string, body: unknown, handlers: SSEHandlers): Promise<void> {
  const res = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
  })
  if (!res.ok || !res.body) throw new Error('请求失败')

  const reader = res.body.getReader()
  const decoder = new TextDecoder()
  let buffer = ''

  while (true) {
    const { done, value } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parts = buffer.split('\n\n')
    buffer = parts.pop() ?? ''
    for (const part of parts) {
      const evt = parseEvent(part)
      if (!evt) continue
      if (evt.event === 'message') handlers.onMessage(evt.data)
      else if (evt.event === 'done') handlers.onDone()
      else if (evt.event === 'interview_end') handlers.onInterviewEnd()
    }
  }
}

function parseEvent(raw: string): { event: string; data: string } | null {
  let event = 'message'
  const dataLines: string[] = []
  for (const line of raw.split('\n')) {
    if (line.startsWith('event:')) event = line.slice(6).trim()
    else if (line.startsWith('data:')) dataLines.push(line.slice(5).trim())
  }
  if (dataLines.length === 0) return null
  return { event, data: dataLines.join('\n') }
}
```

- [ ] **Step 3: 构建校验**

Run: `cd frontend && npm run build`
Expected: 无类型错误。

- [ ] **Step 4: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add frontend/src/api
git commit -m "feat: frontend api client and sse reader"
```

---

### Task 14: 路由 + 全局样式 + 上传页

**Files:**
- Modify: `frontend/src/App.tsx`（替换为路由）
- Modify: `frontend/src/index.css`（替换为完整样式）
- Create: `frontend/src/pages/UploadPage.tsx`

- [ ] **Step 1: 替换 App.tsx 为路由**

```tsx
import { BrowserRouter, Routes, Route, Navigate } from 'react-router-dom'
import UploadPage from './pages/UploadPage'
import InterviewPage from './pages/InterviewPage'
import ReportPage from './pages/ReportPage'

export default function App() {
  return (
    <BrowserRouter>
      <Routes>
        <Route path="/" element={<UploadPage />} />
        <Route path="/interview/:id" element={<InterviewPage />} />
        <Route path="/report/:id" element={<ReportPage />} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </BrowserRouter>
  )
}
```

- [ ] **Step 2: 替换 index.css 为完整样式（简约克制）**

```css
:root {
  --bg: #fafafa;
  --surface: #ffffff;
  --border: #e5e7eb;
  --text: #111827;
  --text-dim: #6b7280;
  --accent: #312e81;
  --accent-soft: #eef2ff;
  --danger: #dc2626;
}

* { box-sizing: border-box; }
html, body, #root { height: 100%; margin: 0; }
body {
  background: var(--bg);
  color: var(--text);
  font-family: -apple-system, BlinkMacSystemFont, "Segoe UI", "PingFang SC", "Microsoft YaHei", sans-serif;
  -webkit-font-smoothing: antialiased;
}
button { cursor: pointer; }

.page {
  max-width: 720px;
  margin: 0 auto;
  padding: 40px 20px;
  display: flex;
  flex-direction: column;
  gap: 20px;
}
.page h1 { margin: 0; font-size: 28px; }
.sub { color: var(--text-dim); margin: 0; }

.upload-zone {
  display: flex; align-items: center; justify-content: center;
  padding: 48px; border: 2px dashed var(--border); border-radius: 12px;
  background: var(--surface); color: var(--text-dim); cursor: pointer; font-size: 16px;
}
.upload-zone:hover { border-color: var(--accent); color: var(--accent); }

.primary {
  padding: 12px 24px; background: var(--accent); color: #fff;
  border: none; border-radius: 8px; font-size: 16px;
}
.primary:disabled { opacity: 0.5; cursor: not-allowed; }

.status { color: var(--text-dim); }
.error { color: var(--danger); }

.chat {
  display: flex; flex-direction: column; gap: 16px;
  padding: 24px; background: var(--surface); border: 1px solid var(--border);
  border-radius: 12px; min-height: 60vh; max-height: 70vh; overflow-y: auto;
}
.bubble { max-width: 85%; }
.bubble.interviewer { align-self: flex-start; }
.bubble.candidate { align-self: flex-end; }
.bubble .who { font-size: 12px; color: var(--text-dim); margin-bottom: 4px; }
.bubble .body { padding: 12px 16px; border-radius: 12px; font-size: 15px; line-height: 1.6; }
.bubble.interviewer .body { background: var(--accent-soft); }
.bubble.candidate .body { background: #f3f4f6; }
.bubble .body pre { background: #111827; color: #f9fafb; padding: 12px; border-radius: 8px; overflow-x: auto; }
.bubble .body code { font-family: ui-monospace, monospace; }

.input-row { display: flex; gap: 8px; }
.input-row textarea {
  flex: 1; resize: none; padding: 12px; border: 1px solid var(--border);
  border-radius: 8px; font-size: 15px; font-family: inherit; min-height: 48px;
}

.card { background: var(--surface); border: 1px solid var(--border); border-radius: 12px; padding: 20px; }
.card h2 { margin: 0 0 12px; font-size: 18px; }
.scores { display: grid; grid-template-columns: repeat(auto-fill, minmax(120px, 1fr)); gap: 12px; }
.score { display: flex; flex-direction: column; gap: 4px; padding: 12px; border: 1px solid var(--border); border-radius: 8px; }
.score span { font-size: 13px; color: var(--text-dim); }
.score strong { font-size: 24px; }
.card ul { margin: 0; padding-left: 20px; line-height: 1.8; }
```

- [ ] **Step 3: 写 UploadPage.tsx**

```tsx
import { useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { uploadResume, getResumeStatus, createInterview } from '../api/client'

type Phase = 'idle' | 'parsing' | 'parsed' | 'error'

export default function UploadPage() {
  const navigate = useNavigate()
  const [phase, setPhase] = useState<Phase>('idle')
  const [error, setError] = useState('')
  const [resumeId, setResumeId] = useState<number | null>(null)

  async function onUpload(file: File) {
    setPhase('parsing')
    setError('')
    try {
      const { resumeId } = await uploadResume(file)
      setResumeId(resumeId)
      await poll(resumeId)
    } catch (e) {
      setError(e instanceof Error ? e.message : '上传失败')
      setPhase('error')
    }
  }

  async function poll(id: number) {
    for (let i = 0; i < 60; i++) {
      await new Promise((r) => setTimeout(r, 1000))
      const s = await getResumeStatus(id)
      if (s.status === 'PARSED') {
        setPhase('parsed')
        return
      }
      if (s.status === 'FAILED') {
        setError('简历解析失败，请重试')
        setPhase('error')
        return
      }
    }
    setError('解析超时，请重试')
    setPhase('error')
  }

  async function onStart() {
    if (!resumeId) return
    try {
      const { sessionId } = await createInterview(resumeId)
      navigate(`/interview/${sessionId}`)
    } catch (e) {
      setError(e instanceof Error ? e.message : '创建面试失败')
    }
  }

  return (
    <main className="page">
      <h1>面经Mock</h1>
      <p className="sub">多 Agent 技术面试仿真系统</p>

      {phase === 'idle' && (
        <label className="upload-zone">
          上传简历（PDF / Word）
          <input
            type="file"
            accept=".pdf,.doc,.docx"
            style={{ display: 'none' }}
            onChange={(e) => {
              const f = e.target.files?.[0]
              if (f) onUpload(f)
            }}
          />
        </label>
      )}

      {phase === 'parsing' && <p className="status">正在解析简历…</p>}
      {phase === 'parsed' && (
        <button className="primary" onClick={onStart}>
          开始面试
        </button>
      )}
      {error && <p className="error">{error}</p>}
    </main>
  )
}
```

- [ ] **Step 4: 构建校验**

Run: `cd frontend && npm run build`
Expected: 报错（`InterviewPage`、`ReportPage` 尚未创建，属预期，Task 15/16 补齐后消失）。

- [ ] **Step 5: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add frontend/src
git commit -m "feat: routing styles and upload page"
```

---

### Task 15: 面试对话页 + 消息气泡

**Files:**
- Create: `frontend/src/components/ChatBubble.tsx`
- Create: `frontend/src/pages/InterviewPage.tsx`

- [ ] **Step 1: 写 ChatBubble.tsx（markdown 渲染）**

```tsx
import ReactMarkdown from 'react-markdown'

export default function ChatBubble({ role, content }: { role: string; content: string }) {
  const isInterviewer = role === 'INTERVIEWER'
  return (
    <div className={`bubble ${isInterviewer ? 'interviewer' : 'candidate'}`}>
      <div className="who">{isInterviewer ? '面试官' : '你'}</div>
      <div className="body">
        <ReactMarkdown>{content}</ReactMarkdown>
      </div>
    </div>
  )
}
```

- [ ] **Step 2: 写 InterviewPage.tsx（流式对话）**

```tsx
import { useEffect, useRef, useState } from 'react'
import { useParams, useNavigate } from 'react-router-dom'
import { getMessages, MessageDto } from '../api/client'
import { streamPost, SSEHandlers } from '../api/sse'
import ChatBubble from '../components/ChatBubble'

export default function InterviewPage() {
  const { id } = useParams()
  const sessionId = Number(id)
  const navigate = useNavigate()
  const [messages, setMessages] = useState<MessageDto[]>([])
  const [streaming, setStreaming] = useState('')
  const [input, setInput] = useState('')
  const [busy, setBusy] = useState(true)
  const bufRef = useRef('')

  useEffect(() => {
    ;(async () => {
      try {
        const existing = await getMessages(sessionId)
        setMessages(existing)
        if (existing.length === 0) await streamOpening()
        else setBusy(false)
      } catch {
        setBusy(false)
      }
    })()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  function handlers(): SSEHandlers {
    return {
      onMessage: (t) => {
        bufRef.current += t
        setStreaming(bufRef.current)
      },
      onDone: () => {
        setMessages((m) => [...m, { role: 'INTERVIEWER', content: bufRef.current, layer: null }])
        bufRef.current = ''
        setStreaming('')
        setBusy(false)
      },
      onInterviewEnd: () => {
        navigate(`/report/${sessionId}`)
      },
    }
  }

  async function streamOpening() {
    bufRef.current = ''
    await streamPost(`/api/interviews/${sessionId}/start`, {}, handlers())
  }

  async function send() {
    if (!input.trim() || busy) return
    const answer = input.trim()
    setInput('')
    setMessages((m) => [...m, { role: 'CANDIDATE', content: answer, layer: null }])
    setBusy(true)
    bufRef.current = ''
    await streamPost(`/api/interviews/${sessionId}/answer`, { content: answer }, handlers())
  }

  return (
    <main className="page" style={{ maxWidth: 860 }}>
      <div className="chat">
        {messages.map((m, i) => (
          <ChatBubble key={i} role={m.role} content={m.content} />
        ))}
        {streaming && <ChatBubble role="INTERVIEWER" content={streaming} />}
      </div>
      <div className="input-row">
        <textarea
          value={input}
          disabled={busy}
          placeholder={busy ? '面试官正在提问…' : '输入你的回答，Enter 发送（Shift+Enter 换行）'}
          onChange={(e) => setInput(e.target.value)}
          onKeyDown={(e) => {
            if (e.key === 'Enter' && !e.shiftKey) {
              e.preventDefault()
              send()
            }
          }}
        />
        <button className="primary" disabled={busy || !input.trim()} onClick={send}>
          发送
        </button>
      </div>
    </main>
  )
}
```

- [ ] **Step 3: 构建校验**

Run: `cd frontend && npm run build`
Expected: 报错（`ReportPage` 尚未创建，Task 16 补齐后消失）。

- [ ] **Step 4: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add frontend/src
git commit -m "feat: interview chat page with streaming"
```

---

### Task 16: 复盘报告页

**Files:**
- Create: `frontend/src/pages/ReportPage.tsx`

- [ ] **Step 1: 写 ReportPage.tsx**

```tsx
import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { getReport, Report } from '../api/client'

export default function ReportPage() {
  const { id } = useParams()
  const [report, setReport] = useState<Report | null>(null)
  const [error, setError] = useState('')

  useEffect(() => {
    getReport(Number(id))
      .then(setReport)
      .catch((e) => setError(e instanceof Error ? e.message : '加载报告失败'))
  }, [id])

  if (error) return <main className="page"><p className="error">{error}</p></main>
  if (!report) return <main className="page"><p className="status">生成报告中…</p></main>

  return (
    <main className="page">
      <h1>复盘报告</h1>

      <section className="card">
        <h2>多维评分</h2>
        <div className="scores">
          {Object.entries(report.scores).map(([k, v]) => (
            <div key={k} className="score">
              <span>{k}</span>
              <strong>{v}</strong>
            </div>
          ))}
        </div>
      </section>

      <section className="card">
        <h2>薄弱点</h2>
        <ul>{report.weaknesses.map((w, i) => <li key={i}>{w}</li>)}</ul>
      </section>

      <section className="card">
        <h2>改进建议</h2>
        <ul>{report.suggestions.map((s, i) => <li key={i}>{s}</li>)}</ul>
      </section>

      <button className="primary" onClick={() => (window.location.href = '/')}>
        再来一次
      </button>
    </main>
  )
}
```

- [ ] **Step 2: 全量构建校验（前端应无报错）**

Run: `cd frontend && npm run build`
Expected: 无类型错误，产出 `dist/`。

- [ ] **Step 3: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add frontend/src
git commit -m "feat: report page"
```

---

## 阶段五（M5）：容器化交付

### Task 17: 多阶段 Dockerfile（前端打包进后端镜像）

**Files:**
- Create: `Dockerfile`（仓库根目录，构建上下文为根目录）

> 说明：文件结构总览里写的 `backend/Dockerfile` 调整为根目录 `Dockerfile`，因为构建需要同时访问 frontend 与 backend。

- [ ] **Step 1: 写 Dockerfile**

```dockerfile
# 阶段一：构建前端
FROM node:20-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 阶段二：构建后端（把前端 dist 打进静态资源）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN mvn -q -DskipTests package

# 阶段三：运行（单容器单进程）
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
```

> 注意：`npm ci` 依赖 `package-lock.json`（Task 2 的 `npm install` 已生成并提交）。

- [ ] **Step 2: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add Dockerfile
git commit -m "feat: multi-stage dockerfile"
```

---

### Task 18: Docker Compose + 环境变量 + README

**Files:**
- Create: `docker-compose.yml`
- Create: `.env.example`
- Create: `README.md`

- [ ] **Step 1: 写 docker-compose.yml（中间件端口不对外暴露，仅 app 暴露 8080）**

```yaml
services:
  mysql:
    image: mysql:8.0
    environment:
      MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-root}
      MYSQL_DATABASE: ${MYSQL_DATABASE:-mockinterview}
      MYSQL_USER: ${MYSQL_USER:-mock}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-mock}
    volumes:
      - mysql_data:/var/lib/mysql
    healthcheck:
      test: ["CMD", "mysqladmin", "ping", "-h", "localhost", "-p${MYSQL_ROOT_PASSWORD:-root}"]
      interval: 10s
      timeout: 5s
      retries: 10

  redis:
    image: redis:7-alpine
    volumes:
      - redis_data:/data
    healthcheck:
      test: ["CMD", "redis-cli", "ping"]
      interval: 10s
      timeout: 5s
      retries: 10

  chromadb:
    image: chromadb/chroma:latest
    volumes:
      - chroma_data:/chroma/chroma

  app:
    build: .
    ports:
      - "${APP_PORT:-8080}:8080"
    environment:
      MYSQL_HOST: mysql
      MYSQL_PORT: 3306
      MYSQL_DATABASE: ${MYSQL_DATABASE:-mockinterview}
      MYSQL_USER: ${MYSQL_USER:-mock}
      MYSQL_PASSWORD: ${MYSQL_PASSWORD:-mock}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      CHROMA_BASE_URL: http://chromadb:8000
      DEEPSEEK_API_KEY: ${DEEPSEEK_API_KEY}
      DEEPSEEK_BASE_URL: ${DEEPSEEK_BASE_URL:-https://api.deepseek.com}
      DEEPSEEK_MODEL_NAME: ${DEEPSEEK_MODEL_NAME:-deepseek-flash}
    depends_on:
      mysql:
        condition: service_healthy
      redis:
        condition: service_healthy
      chromadb:
        condition: service_started

volumes:
  mysql_data:
  redis_data:
  chroma_data:
```

- [ ] **Step 2: 写 .env.example**

```
# DeepSeek 模型密钥（必填）
DEEPSEEK_API_KEY=sk-your-key-here
DEEPSEEK_BASE_URL=https://api.deepseek.com
DEEPSEEK_MODEL_NAME=deepseek-flash

# MySQL
MYSQL_DATABASE=mockinterview
MYSQL_USER=mock
MYSQL_PASSWORD=mock
MYSQL_ROOT_PASSWORD=root

# 对外端口
APP_PORT=8080
```

- [ ] **Step 3: 写 README.md**

```markdown
# 面经Mock · 多 Agent 技术面试仿真系统

对着自己的简历跑一遍完整的技术面试，被连环追问到答不上来的痛点，结束后拿到薄弱点诊断与改进建议。

## 快速开始

1. 配置密钥：

```bash
cp .env.example .env
# 编辑 .env，填入 DEEPSEEK_API_KEY
```

2. 一键启动：

```bash
docker compose up -d --build
```

3. 打开浏览器访问 <http://localhost:8080>，上传简历开始面试。

## 技术栈

Spring Boot · LangChain4j · DeepSeek · Apache Tika · MySQL · Redis · ChromaDB · React + Vite · Docker Compose
```

- [ ] **Step 4: 提交**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add docker-compose.yml .env.example README.md
git commit -m "feat: docker compose orchestration and docs"
```

---

### Task 19: 端到端冒烟测试（一键启动 + 完整闭环验证）

**目的：** 验证 `docker compose up -d --build` 一键启动后，「上传简历 → 开始面试 → 连环追问 → 复盘输出」全链路可用。此任务为集成验证，不写自动化断言，逐条用 curl 观察输出。

- [ ] **Step 1: 准备样例简历与配置**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
printf '张三\n5 年后端开发经验，主要使用 Java 技术栈。\n\n项目一：电商订单系统\n职责：负责下单、支付回调、库存扣减模块的设计与开发。\n技术栈：Spring Boot、MySQL、Redis、Kafka。\n难点：高并发下的库存扣减，采用 Redis + Lua 保证原子性，Kafka 削峰。\n\n项目二：统一配置中心\n职责：负责配置下发与热更新。\n技术栈：Spring Boot、ZooKeeper。\n' > sample-resume.txt

cp .env.example .env
# 手动编辑 .env，把 DEEPSEEK_API_KEY 填成真实密钥
```

- [ ] **Step 2: 一键启动全部服务**

```bash
docker compose up -d --build
```
Expected: 4 个容器（app、mysql、redis、chromadb）启动。首次会拉取镜像并构建 app（几分钟）。

- [ ] **Step 3: 等待就绪并检查健康**

```bash
docker compose ps
curl -s http://localhost:8080/api/health
```
Expected: `docker compose ps` 显示 mysql/redis 为 healthy、app 为 running；curl 返回 `{"status":"UP"}`。

- [ ] **Step 4: 上传简历并等待解析**

```bash
curl -s -F "file=@sample-resume.txt" http://localhost:8080/api/resumes
# 期望 {"resumeId":1}

sleep 8
curl -s http://localhost:8080/api/resumes/1
```
Expected: 第二次 curl 返回 `status:"PARSED"`，且 `parsed.projects` 含「电商订单系统」。

- [ ] **Step 5: 创建面试并流式开场**

```bash
curl -s -X POST http://localhost:8080/api/interviews -H 'Content-Type: application/json' -d '{"resumeId":1}'
# 期望 {"sessionId":1}

curl -N -X POST http://localhost:8080/api/interviews/1/start
```
Expected: 第二条命令流式输出 `event:message`（开场问题 token）+ `event:done`。

- [ ] **Step 6: 连续回答几轮，观察追问与跳转**

```bash
curl -N -X POST http://localhost:8080/api/interviews/1/answer \
  -H 'Content-Type: application/json' \
  -d '{"content":"我负责下单流程，高并发下用 Redis 加 Lua 脚本原子扣减库存，Kafka 削峰"}'
```
Expected: 流式输出下一问。重复该命令若干次（回答内容可换成「这块我不太清楚」「主要用 Spring 自带的缓存」等），观察：答对时问题变深（层递进）、答错时换角度、连续卡壳时跳转到下一个项目，最终输出 `event:interview_end`。

- [ ] **Step 7: 获取复盘报告**

```bash
curl -s http://localhost:8080/api/interviews/1/report
```
Expected: 返回 `scores`（含技术深度/总体等）、`weaknesses`、`suggestions` 三项非空。

- [ ] **Step 8: 浏览器走一遍完整 UI**

浏览器打开 `http://localhost:8080`，上传简历 → 开始面试 → 逐轮对话 → 结束跳转报告页。确认三页均正常、样式简约、无控制台报错。

- [ ] **Step 9: 清理（保留数据卷，便于下次启动）**

```bash
docker compose down
```

- [ ] **Step 10: 提交样例简历（可选）**

```bash
cd "D:/BaiduNetdiskDownload/the engineering/coding"
git add sample-resume.txt
git commit -m "chore: sample resume for smoke test"
```

---

## 完成判定

P0 核心闭环完成的标准：**在仅配置 `.env` 密钥后，`docker compose up -d --build` 一条命令启动，浏览器里能走完「上传简历 → 面试 → 复盘」全流程**，且后端 `mvn test` 全部通过（状态机、解析器、编排、契约测试）。

