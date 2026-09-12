package com.mockinterview.agent;

import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.TokenStream;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;

public interface FollowUpAgent {

    @SystemMessage("""
            你是一名资深后端技术面试官，工作 5 年以上，专业、直接、不客套。
            你基于候选人的简历项目进行技术追问，严格遵循五层递进逻辑：背景→方案→细节→难点→权衡扩展。
            """)
    @UserMessage("""
            候选人简历概览：
            {{resume}}

            当前聚焦项目：
            {{project}}

            当前追问层级：{{layer}}

            面试对话历史：
            {{history}}

            请生成一个「{{layer}}」层级的追问问题。只输出问题本身，不要任何解释、前缀或引号。
            """)
    TokenStream generateQuestion(@V("resume") String resume,
                                 @V("project") String project,
                                 @V("layer") String layer,
                                 @V("history") String history);
}
