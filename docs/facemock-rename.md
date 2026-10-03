# FaceMock 目录及 Docker 改名

本次将项目从 `D:\BaiduNetdiskDownload\the engineering\coding` 迁移到 `D:\BaiduNetdiskDownload\the engineering\facemock`。源代码、Git 历史、未提交文件和本地 `.env` 一起保留。

## 当前名称

| 项目 | 名称 |
| --- | --- |
| 目录 / Compose 项目 | `facemock` |
| 应用镜像 | `facemock-app:latest` |
| 应用容器 | `facemock-app-1` |
| MySQL / Redis / Chroma 容器 | `facemock-mysql-1` / `facemock-redis-1` / `facemock-chromadb-1` |
| 后端构建产物 | `facemock-backend-0.1.0.jar` |
| 前端包 | `facemock-frontend` |
| 网页名称 | `FaceMock` |

官方 MySQL、Redis 和 Chroma 镜像保留原名称；Java 包名和已有数据库名称保持原值，避免影响已有数据和引用。

## 日常启动

在新目录执行：

```powershell
Set-Location 'D:\BaiduNetdiskDownload\the engineering\facemock'
docker compose up -d --build
docker compose ps
```

访问地址仍为 `http://localhost:8080`。IDE 或 Codex 如果仍保存旧目录路径，请重新打开新目录。

## 数据保留

服务停止后复制数据卷，随后使用新卷启动。旧卷作为迁移前备份保留，不会自动删除。

| 原卷（备份） | 当前使用的卷 |
| --- | --- |
| `coding_mysql_data` | `facemock_mysql_data` |
| `coding_redis_data` | `facemock_redis_data` |
| `coding_chroma_rag_data` | `facemock_chroma_rag_data` |
| `coding_speech_recordings` | `facemock_speech_recordings` |
| `coding_model_settings` | `facemock_model_settings` |

原来的未使用卷 `coding_chroma_data` 也保留。迁移前应用镜像保存在 `facemock-app:before-project-rename`；原有应用备份镜像使用 `facemock-app` 名称保留相同标签。

不要执行 `docker compose down -v`，它会删除当前数据卷。旧卷是迁移时的快照，不会接收后续新练习。若需要回退数据，应先停止服务并备份当前卷，再在独立配置中显式挂载上述旧卷；只切换旧镜像不会自动恢复旧数据。

历史阶段报告中的旧容器名、截图和日志描述当时的验收环境。现在运行命令请使用新目录和当前名称。

## 本次验收

- 前端生产构建、3 项录音检查及 Docker 镜像构建通过；Git HEAD 保持不变。
- 五个数据卷逐文件内容摘要和权限一致。
- 新部署 `/api/health` 返回 `UP`；个人数据导出摘要与迁移前完全一致。
- 用户模型设置及讯飞配置保持一致；知识库可用，仍为 190 条片段。
- Docker Compose 工作路径为新目录；旧容器、旧网络及 `coding-app` 镜像标签已移除，备份镜像及旧数据卷保留。
- 网页标题及侧栏已显示 FaceMock，见 [页面截图](screenshots/facemock-renamed.jpg)；只含计数和一致性结果的 [验收数据](facemock-rename-acceptance.json) 不包含密钥或简历内容。

Windows 的当前 Codex 工具进程仍占用旧 `coding` 工作目录。因此文件已全部移入 `facemock`，旧 `coding` 目录只剩空壳。关闭当前 Codex 会话后可删除这个空目录，并在 Codex 中重新添加或打开 `facemock` 项目。不要把新文件保存到旧目录。
