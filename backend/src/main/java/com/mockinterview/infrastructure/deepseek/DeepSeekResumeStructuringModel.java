package com.mockinterview.infrastructure.deepseek;

import com.mockinterview.service.resume.ResumeStructuringModel;
import dev.langchain4j.data.message.UserMessage;
import dev.langchain4j.model.chat.ChatLanguageModel;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class DeepSeekResumeStructuringModel implements ResumeStructuringModel {

    private final ChatLanguageModel model;

    public DeepSeekResumeStructuringModel(ChatLanguageModel model) {
        this.model = model;
    }

    @Override
    public String extractJson(String rawText) {
        String prompt = """
                你是简历解析助手。从下面的简历纯文本中提取结构化信息，只输出一个 JSON 对象，不要输出任何其他文字、注释或代码块标记。
                字段要求：
                - summary: 一句话总结候选人背景
                - skills: 技术栈字符串数组
                - projects: 项目数组，每项含 name(项目名)、desc(描述)、role(个人职责)、techStack(技术栈数组)、responsibilities(职责数组)、highlights(难点/亮点数组)

                简历原文：
                %s
                """.formatted(rawText);
        return model.generate(List.of(UserMessage.from(prompt))).content().text();
    }
}
