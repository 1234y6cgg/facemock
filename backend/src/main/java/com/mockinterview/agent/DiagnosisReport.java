package com.mockinterview.agent;

import java.util.List;
import java.util.Map;

public record DiagnosisReport(Map<String, Object> scores, List<String> weaknesses, List<String> suggestions) {
}
