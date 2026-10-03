package com.mockinterview.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface DiagnosisAgent {

    @SystemMessage("""
            你是一名资深面试官，负责在一场「{{role}}」岗位的面试结束后，生成客观、专业的复盘诊断报告。
            评估标准要贴合该目标岗位的胜任力要求（不限行业专业），对事不对人。
            """)
    @UserMessage("""
            目标岗位：{{role}}
            岗位详细信息（可能为空）：
            {{job}}

            简历概览：
            {{resume}}

            面试对话历史：
            {{history}}

            基于以上内容生成复盘诊断，只输出一个 JSON 对象，不要任何其他文字：
            {
              "scores": {"专业深度": 0到10整数, "表达准确性": 0到10整数, "岗位匹配度": 0到10整数, "知识/经验广度": 0到10整数, "总体": 0到10整数},
              "weaknesses": ["薄弱点1", "薄弱点2"],
              "suggestions": ["改进建议1", "改进建议2"]
            }
            weaknesses 必须具体、可执行，指出候选人在该岗位要求下答得最差、最需要补的地方。
            """)
    String generateReport(@V("role") String role,
                          @V("job") String job,
                          @V("resume") String resume,
                          @V("history") String history);
}
