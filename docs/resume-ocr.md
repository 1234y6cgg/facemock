# 图片与扫描简历识别

文字 Word 使用 Tika 提取；PDF 使用 PDFBox 逐页提取原生文字。图片简历直接送到本地 PaddleOCR；PDF 中原生文字少于 80 字且包含图片，或没有原生文字的页面，渲染为 144 DPI 图片后补充 OCR。保留页面顺序，混合 PDF 不会仅凭第一页有文字就跳过后续扫描页。

## 使用

```sh
docker compose up -d --build
```

在模拟面试 `/mock` 或项目话术 `/projects` 上传 PDF、DOC、DOCX、PNG、JPG/JPEG 或 WebP。上传后立即返回简历 ID，后台先提取文字，再调用用户配置的文本模型结构化解析。解析完成后点击“核对文字”或“查看提取文字”，核对姓名、技能和项目事实再开始练习。

使用 PaddleOCR 3.7.0 / PaddlePaddle 3.3.1，明确选择 PP-OCRv5 mobile 中文模型及 CPU 推理。模型在镜像构建时从官方来源下载，缓存放在 `ocr_models` 卷；OCR 容器不发布宿主机端口、不调用云端识别、不需要 OCR Key。文本结构化解析仍使用用户选定的大模型，只有 OCR 后的文字进入原有结构化解析流程。

## 配置

| 环境变量 | 默认值 | 含义 |
| --- | --- | --- |
| `OCR_ENABLED` | `true` | 允许图片和扫描页 OCR；禁用后文字 Word/PDF 仍可解析 |
| `OCR_TIMEOUT_SECONDS` | `120` | 单张图片或整份 PDF 的 OCR 时间预算 |
| `OCR_MAX_PDF_PAGES` | `10` | PDF 页数上限 |
| `OCR_BASE_URL` | `http://localhost:8001` | 原生 Java 的 OCR 地址；Compose 固定为内部 `http://ocr:8000` |

原生运行 Java 时可先运行 Python 服务：

```sh
python -m pip install -r ocr/requirements.txt
cd ocr
python -m uvicorn app:app --host 127.0.0.1 --port 8001 --workers 1
```

不要把 OCR 服务单独暴露到公网。Docker 使用一个推理进程、串行模型推理及有界请求槽；Java 使用专用的两个解析线程与四项等待队列，避免大量上传堆积。

## 查询与原文

- `GET /api/resumes/ocr/status`：是否启用及 OCR 服务是否可用。
- `GET /api/resumes/{id}`：`PARSING` / `PARSED` / `FAILED`，失败原因 `errorMessage`，提取方式 `TEXT` / `OCR` / `MIXED`。
- `GET /api/resumes/{id}/text`：已提取的文字，禁止缓存；尚未提取时返回 204。
- `GET /api/resumes/{id}/file`：原文件。

服务不可达、OCR 超时、内容过少、页数过多或文件损坏会明确失败，不会把不完整的扫描 PDF 当作成功。原文件保留；结构化模型失败时已提取的文字也保留。服务重启会把中断的解析标为失败，避免永久停留在解析中。

## 范围

文件上限 6 MB；单张图片或 PDF 渲染页上限 1200 万像素；识别文字最多 10 万字符。低于 0.5 置信度的 OCR 行不进入结果。无法解析密码保护 PDF。Word 中只有图片、复杂双栏、表格或原生文字较多但仍嵌入大幅截图的 PDF，目前不能保证完整识别或正确阅读顺序；需要本人核对结果。本次接入通用 OCR，不包含 PP-Structure 版面解析、OCR 准确率标定或自动纠正简历事实。

官方依据：[PaddleOCR 快速开始](https://github.com/PaddlePaddle/PaddleOCR/blob/main/docs/quick_start.en.md)、[OCR 模型及参数](https://github.com/PaddlePaddle/PaddleOCR/blob/v3.3.2/docs/version3.x/pipeline_usage/OCR.en.md)。

## 本次验收

- 后端完整回归：243 项，195 项通过，48 项可选外部依赖检查跳过，0 失败。
- OCR Python 检查：3 项通过；前端生产构建及 3 项录音检查通过。
- 使用虚构简历在实际 CPU 模型上识别 PNG、JPEG、WebP，均识别到中文订单项目和 Redis 技术栈。
- 实际应用上传 PNG、扫描 PDF、混合 PDF 均完成 OCR 和真实文本模型结构化解析；混合 PDF 保留原生文字页及扫描页。
- 文件原始字节保持一致，核对文字接口禁止缓存，伪装 PDF 返回 400。
- 三条虚构验收记录已清理，升级及清理后原有用户数据摘要与升级前完全一致；知识库仍为 190 条片段。
- 已更新运行中的应用及新增 OCR 容器。应用回退镜像为 `facemock-app:before-paddleocr`；数据库新增的两个可空字段兼容旧版本。OCR 缓存位于 `facemock_ocr_models`，原有数据卷继续使用。

[验收结果摘要](paddleocr-acceptance.json) 只包含虚构样例与检查结果。本轮验证真实模型和 HTTP 上传链路，以及前端构建；未额外执行浏览器交互检查，也不代表 OCR 通用准确率。
