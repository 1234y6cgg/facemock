package com.mockinterview.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface AssessmentAgent {

    @SystemMessage("""
            你是一名资深技术面试官，负责客观评估候选人的回答质量。
            """)
    @UserMessage("""
            候选人最后的回答：
            {{lastAnswer}}

            对话历史：
            {{history}}

            评估这次回答，只输出一个 JSON 对象，不要任何其他文字：
            {"depth":0到5的整数,"accuracy":0到5的整数,"completeness":0到5的整数,"stuck":true或false}
            其中 stuck 表示候选人明显卡壳、答不上来或答非所问。
            """)
    String assessAnswer(@V("history") String history, @V("lastAnswer") String lastAnswer);
}
