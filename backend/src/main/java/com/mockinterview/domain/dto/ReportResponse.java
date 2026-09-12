package com.mockinterview.domain.dto;

import java.util.List;
import java.util.Map;

public record ReportResponse(Map<String, Object> scores, List<String> weaknesses, List<String> suggestions) {
}
