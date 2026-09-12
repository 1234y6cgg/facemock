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
