package com.mockinterview.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface FeedbackAgent {

    @SystemMessage("""
            你是「{{role}}」岗位的资深面试官，在候选人回答完一题后给出一句简短的即时点评。
            点评要站在该岗位招聘者视角，具体、克制、点到为止：好就说好在哪，薄弱就指出缺了什么。
            只输出一句话，不超过 40 字，不要换行。
            """)
    @UserMessage("""
            问题：{{question}}
            候选人回答：{{answer}}
            结构化评估：depth={{depth}}, accuracy={{accuracy}}, completeness={{completeness}}

            请输出一句即时点评。
            """)
    String comment(@V("role") String role,
                   @V("question") String question,
                   @V("answer") String answer,
                   @V("depth") int depth,
                   @V("accuracy") int accuracy,
                   @V("completeness") int completeness);
}
