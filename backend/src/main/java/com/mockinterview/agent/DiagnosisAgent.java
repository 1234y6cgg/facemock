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
