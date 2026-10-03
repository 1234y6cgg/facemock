package com.mockinterview.infrastructure.ocr;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.config.OcrProperties;
import com.mockinterview.service.resume.ResumeExtractionException;
import org.springframework.stereotype.Component;
import java.net.URI;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.UUID;

@Component
public class PaddleOcrClient {
    private final OcrProperties properties;
    private final ObjectMapper json;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    public PaddleOcrClient(OcrProperties properties, ObjectMapper json) { this.properties = properties; this.json = json; }

    public String extract(byte[] image, int timeoutSeconds) {
        if (!properties.isEnabled()) throw new ResumeExtractionException("图片或扫描简历需要启用 PaddleOCR，请启用 OCR 服务后重新上传。");
        try {
            String boundary = "facemock-" + UUID.randomUUID();
            byte[] start = ("--" + boundary + "\r\nContent-Disposition: form-data; name=\"file\"; filename=\"page.png\"\r\nContent-Type: application/octet-stream\r\n\r\n").getBytes(StandardCharsets.UTF_8);
            byte[] end = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            var request = HttpRequest.newBuilder(endpoint("/extract"))
                    .timeout(Duration.ofSeconds(Math.max(1, Math.min(properties.getTimeoutSeconds(), timeoutSeconds))))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(HttpRequest.BodyPublishers.concat(HttpRequest.BodyPublishers.ofByteArray(start),
                            HttpRequest.BodyPublishers.ofByteArray(image), HttpRequest.BodyPublishers.ofByteArray(end))).build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofInputStream());
            try (var body = response.body()) {
                if (response.statusCode() == 422) throw new ResumeExtractionException("图片无法识别，请使用清晰的 PNG、JPG 或 WebP 简历重新上传。");
                if (response.statusCode() != 200) throw new ResumeExtractionException("PaddleOCR 服务暂时不可用或正在处理其他简历，请稍后重新上传。");
                byte[] bytes = body.readNBytes(600001);
                if (bytes.length > 600000) throw new ResumeExtractionException("识别文字过长，请缩短简历后重新上传。");
                var value = json.readTree(bytes).get("text");
                if (value == null || !value.isTextual() || value.asText().length() > 100000) throw new ResumeExtractionException("OCR 返回结果不完整，请重新上传。");
                return value.asText();
            }
        } catch (ResumeExtractionException e) { throw e; }
        catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new ResumeExtractionException("简历识别已中断，请重新上传。"); }
        catch (HttpTimeoutException e) { throw new ResumeExtractionException("简历识别超时，请减少页数或使用清晰图片后重新上传。"); }
        catch (Exception e) { throw new ResumeExtractionException("无法连接 PaddleOCR 服务，请检查 OCR 容器状态后重新上传。"); }
    }

    public Status status() {
        if (!properties.isEnabled()) return new Status(false, false, "PP-OCRv5_mobile");
        try {
            var request = HttpRequest.newBuilder(endpoint("/health")).timeout(Duration.ofSeconds(2)).GET().build();
            var response = client.send(request, HttpResponse.BodyHandlers.ofString());
            boolean up = response.statusCode() == 200 && "UP".equals(json.readTree(response.body()).path("status").asText());
            return new Status(true, up, "PP-OCRv5_mobile");
        } catch (InterruptedException e) { Thread.currentThread().interrupt(); return new Status(true, false, "PP-OCRv5_mobile"); }
        catch (Exception e) { return new Status(true, false, "PP-OCRv5_mobile"); }
    }
    private URI endpoint(String path) { return URI.create(properties.getBaseUrl().replaceAll("/+$", "") + path); }
    public record Status(boolean enabled, boolean available, String engine) {}
}
