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
