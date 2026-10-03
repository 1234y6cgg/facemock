package com.mockinterview.service.resume;

import com.mockinterview.config.OcrProperties;
import com.mockinterview.infrastructure.ocr.PaddleOcrClient;
import com.mockinterview.infrastructure.tika.TikaTextExtractor;
import org.apache.pdfbox.pdmodel.*;
import org.apache.pdfbox.pdmodel.graphics.form.PDFormXObject;
import org.apache.pdfbox.pdmodel.graphics.image.PDImageXObject;
import org.apache.pdfbox.rendering.*;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import javax.imageio.ImageIO;
import java.io.*;
import java.time.*;
import java.util.*;

@Component
public class ResumeTextExtractor {
    private final TikaTextExtractor tika;
    private final PaddleOcrClient ocr;
    private final OcrProperties properties;
    private static final long MAX_PIXELS = 12_000_000;
    public ResumeTextExtractor(TikaTextExtractor tika, PaddleOcrClient ocr, OcrProperties properties) {
        this.tika = tika; this.ocr = ocr; this.properties = properties;
    }

    public String validate(byte[] bytes, String filename) {
        if (bytes == null || bytes.length == 0) throw new IllegalArgumentException("简历文件为空，请重新选择文件。");
        if (bytes.length > 6 * 1024 * 1024) throw new IllegalArgumentException("简历文件不能超过 6 MB。");
        String name = filename == null ? "" : filename.toLowerCase(Locale.ROOT);
        if (name.endsWith(".pdf") && starts(bytes, new byte[]{37,80,68,70,45})) return "application/pdf";
        if (name.endsWith(".png") && starts(bytes, new byte[]{(byte)137,80,78,71,13,10,26,10})) return "image/png";
        if ((name.endsWith(".jpg") || name.endsWith(".jpeg")) && starts(bytes,new byte[]{(byte)255,(byte)216,(byte)255})) return "image/jpeg";
        if (name.endsWith(".webp") && bytes.length >= 12 && starts(bytes,new byte[]{82,73,70,70}) && bytes[8]==87 && bytes[9]==69 && bytes[10]==66 && bytes[11]==80) return "image/webp";
        if (name.endsWith(".doc") && starts(bytes,new byte[]{(byte)208,(byte)207,17,(byte)224,(byte)161,(byte)177,26,(byte)225})) return "application/msword";
        if (name.endsWith(".docx") && starts(bytes,new byte[]{80,75,3,4})) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        throw new IllegalArgumentException("请选择有效的 PDF、DOC、DOCX、PNG、JPG 或 WebP 简历，文件内容须与格式一致。");
    }

    public Result extract(byte[] bytes, String filename) {
        String type = validate(bytes, filename);
        try {
            if (type.startsWith("image/")) return checked(ocr.extract(bytes, properties.getTimeoutSeconds()), "OCR");
            if (type.equals("application/pdf")) return pdf(bytes);
            return checked(tika.extract(new ByteArrayInputStream(bytes)), "TEXT");
        } catch (ResumeExtractionException e) { throw e; }
        catch (Exception e) { throw new ResumeExtractionException("简历文件无法读取，请确认文件未损坏且没有密码保护。"); }
    }

    private Result pdf(byte[] bytes) throws IOException {
        try (var document = PDDocument.load(bytes)) {
            if (document.isEncrypted()) throw new ResumeExtractionException("请解除 PDF 密码保护后重新上传。");
            if (document.getNumberOfPages() > properties.getMaxPdfPages()) throw new ResumeExtractionException("简历 PDF 最多支持 " + properties.getMaxPdfPages() + " 页，请减少页数后重新上传。");
            var stripper = new PDFTextStripper(); stripper.setSortByPosition(true);
            var renderer = new PDFRenderer(document);
            var text = new StringBuilder(); int nativePages = 0, ocrPages = 0;
            Instant deadline = Instant.now().plusSeconds(properties.getTimeoutSeconds());
            for (int index = 0; index < document.getNumberOfPages(); index++) {
                stripper.setStartPage(index + 1); stripper.setEndPage(index + 1);
                String pageText = stripper.getText(document).trim();
                var page = document.getPage(index);
                boolean scanned = meaningfulChars(pageText) < properties.getMinPageTextChars()
                        && (pageText.isBlank() || hasImage(page.getResources(), 0));
                if (scanned) {
                    var box = page.getCropBox();
                    double pixels = Math.ceil(box.getWidth() * 2) * Math.ceil(box.getHeight() * 2);
                    if (!Double.isFinite(pixels) || pixels <= 0 || pixels > MAX_PIXELS) throw new ResumeExtractionException("PDF 页面尺寸过大，请缩小后重新上传。");
                    int remaining = (int) Duration.between(Instant.now(), deadline).getSeconds();
                    if (remaining <= 0) throw new ResumeExtractionException("简历识别超时，请减少页数后重新上传。");
                    var image = renderer.renderImageWithDPI(index, 144, ImageType.RGB);
                    try (var out = new ByteArrayOutputStream()) {
                        ImageIO.write(image, "png", out);
                        pageText = ocr.extract(out.toByteArray(), remaining);
                    } finally { image.flush(); }
                    ocrPages++;
                } else if (!pageText.isBlank()) nativePages++;
                text.append(pageText).append("\n\n");
                if (text.length() > 100000) throw new ResumeExtractionException("简历文字过长，请缩短后重新上传。");
            }
            return checked(text.toString(), ocrPages == 0 ? "TEXT" : nativePages == 0 ? "OCR" : "MIXED");
        }
    }

    private boolean hasImage(PDResources resources, int depth) throws IOException {
        if (resources == null || depth > 5) return false;
        for (var name : resources.getXObjectNames()) {
            var object = resources.getXObject(name);
            if (object instanceof PDImageXObject) return true;
            if (object instanceof PDFormXObject form && hasImage(form.getResources(), depth + 1)) return true;
        }
        return false;
    }
    private Result checked(String text, String method) {
        if (text == null || meaningfulChars(text) < 30) throw new ResumeExtractionException("未识别到足够的简历文字，请上传清晰且内容完整的简历。");
        if (text.length() > 100000) throw new ResumeExtractionException("简历文字过长，请缩短后重新上传。");
        return new Result(text.trim(), method);
    }
    private int meaningfulChars(String text) { return text.replaceAll("\s", "").length(); }
    private boolean starts(byte[] value, byte[] signature) { return value.length >= signature.length && Arrays.equals(Arrays.copyOf(value, signature.length), signature); }
    public PaddleOcrClient.Status ocrStatus() { return ocr.status(); }
    public record Result(String text, String method) {}
}
