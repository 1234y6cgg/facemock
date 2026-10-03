# 使用自己的模型 Key

网页入口：左下角齿轮“设置”，点击进入模型选择与 Key 配置，路径 `/settings/model`。手机先打开左上角导航，再点击侧栏底部“设置”。当前是个人自部署、单人工作台；配置对本实例生效，并非多用户各自的凭证隔离。

## 使用

1. 选择 DeepSeek、通义千问、豆包、GLM、Kimi、MiniMax，或自定义 OpenAI Chat Completions 兼容服务。
2. 填写该服务的 API Key。预设根地址、模型 ID 可编辑；模型权限、地域和套餐以你自己的平台账号为准。
3. 点击“测试连接”：只发送固定的 JSON 提示，不包含简历、项目材料或回答；会消耗少量模型 tokens，不保存配置。
4. 点击“保存并使用”：不需要重启，新发起的简历解析、模拟面试、单题评估和项目话术请求使用新配置。进行中的单次请求保持原配置；后台评估固定同一份配置直到完成，避免模型名称和实际调用错配。

未配置 Key 时，应用仍能启动并浏览题库和历史，页面提示去配置。模型请求失败保留既有的错误恢复行为，不把失败结果计为掌握。

## 预设依据

2026-10-03 核对以下官方文档；模型列表会变化，预设可手动修改，不宣称兼容所有模型或套餐。

| 服务 | 默认根地址 | 预设模型 | 官方说明 |
| --- | --- | --- | --- |
| DeepSeek | `https://api.deepseek.com` | `deepseek-flash`、`deepseek-pro` | [API 文档](https://api-docs.deepseek.com/) |
| 通义千问 | `https://dashscope.aliyuncs.com/compatible-mode/v1` | `qwen-plus`、`qwen-flash`、`qwen-max` | [兼容接口](https://help.aliyun.com/zh/model-studio/qwen-api-via-openai-chat-completions)；北京旧域名仍可用，也可填写业务空间专属域名 |
| 豆包 | `https://ark.cn-beijing.volces.com/api/v3` | `doubao-seed-2-1-pro-260628` | [方舟兼容接口](https://docs.volcengine.com/docs/ark/compatible-with-openai-sdk?lang=zh)；可以填写自己开通的 Model ID 或接入点 ID |
| GLM | `https://open.bigmodel.cn/api/paas/v4` | `glm-5.3`、`glm-5.3-flash` | [官方快速开始](https://docs.bigmodel.cn/cn/guide/start/quick-start)；通用 API 与 Coding Plan 端点需区分 |
| Kimi | `https://api.moonshot.cn/v1` | `kimi-k3`、`kimi-k2.6` | [模型列表](https://platform.kimi.com/docs/models)；下线模型不作为预设 |
| MiniMax | `https://api.minimax.cn/v1` | `MiniMax-M3`、`MiniMax-M2.7`、`MiniMax-M2.5` | [OpenAI 兼容说明](https://platform.minimax.cn/docs/api-reference/text-openai-api) |

评分请求可关闭 JSON 模式以兼容不支持 `response_format` 的服务；模型输出仍需通过原有结构、候选人原话和来源校验。DeepSeek 推理、Qwen `enable_thinking`、Kimi K3 推理强度、MiniMax 思考分离参数按服务分别设置，不能把 DeepSeek 专属参数发给其他厂商。Kimi K3 默认推理强度设为 low；MiniMax 输出只取最终正文。讯飞 IAT 凭证仍独立，文本模型 Key 不代替 ASR。

## 保存、切换与清除

Key 仅在填写时发送到当前应用后端；后端向你选定的服务发起带 Bearer 凭证的请求。读取设置只返回是否已配置，不返回 Key 或密文；不写浏览器 localStorage/sessionStorage，不包含在练习导出中，不记录请求正文或供应商错误正文。

留空只在同一服务商、同一根地址且已有 Key 时保留。切换服务商或地址必须明确填写新的 Key，避免把旧凭证发送到新地址。地址不允许内嵌用户名/密码、查询参数、片段或 `/chat/completions` 后缀；外部服务使用 HTTPS，本机 localhost 可用 HTTP。请求不跟随重定向。保存和清除带修订号，过期页面返回 409，需刷新后重填。

Key 使用 AES-256-GCM 加密，密文配置 `model.json` 与随机 `master.key` 保存于 `MODEL_SETTINGS_PATH`。Compose 为 `/app/data/model-settings`，持久卷 `facemock_model_settings`。支持文件系统时设置为所有者读写权限，并用临时文件原子替换配置。能读取整个配置卷的人仍可结合主密钥解密；这不是共享服务器的用户隔离方案。

备份/迁移实例需一起保管配置卷的两份文件。缺主密钥或配置损坏时明确失败，不悄悄回退到其他服务。清除 Key 会持久化空凭证，重启后仍未配置，不自动启用旧环境 Key。已有 `.env` 中的密钥不会被该按钮编辑，应自行管理该文件。

## 兼容原部署

全新部署不必填 `DEEPSEEK_API_KEY`，启动后在页面填写即可。旧的 DeepSeek 环境变量继续作为首次配置来源；页面保存优先，重启从持久卷恢复。没有网页保存记录时显示“来自环境配置”，不会把环境 Key 回显到浏览器。

这次没有数据库迁移，只增加配置卷和接口：

| 接口 | 用途 |
| --- | --- |
| `GET /api/settings/model` | 脱敏读取状态，`Cache-Control: no-store` |
| `GET /api/settings/model/presets` | 国内服务预设及官方文档链接 |
| `PUT /api/settings/model` | 保存 `providerId/baseUrl/modelName/apiKey/jsonMode/revision` |
| `POST /api/settings/model/test` | 相同结构测试未保存的配置，只返回安全状态 |
| `DELETE /api/settings/model?revision=N` | 清除凭证并禁止环境回退 |

后端的 `DeepSeekProperties` 和两个历史 `DeepSeek*AssessmentModel` 类名为兼容现有代码与评测保留，实际调用统一通过动态配置与兼容客户端。模型身份记录包含服务商，切换厂商后的重答不会被冒充为同模型对比；既有评估和引用快照保持不变。
