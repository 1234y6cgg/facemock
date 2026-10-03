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
                你是简历解析助手，服务于各个专业、各个行业的求职面试（不限于计算机/技术岗位）。
                从下面的简历纯文本中提取结构化信息，只输出一个 JSON 对象，不要输出任何其他文字、注释或代码块标记。
                字段要求（保持通用，不要假设候选人是技术从业者）：
                - summary: 一句话总结候选人的专业背景、学历与核心优势
                - skills: 核心能力/专业技能字符串数组，可以是技术栈、专业知识、工具软件、方法论、语言、证书能力等任意与求职相关的能力
                - projects: 候选人的核心经历数组，不局限于"项目"，也包括实习经历、工作经历、科研、竞赛、社会实践、学生工作、作品集、教学/临床/实训经历等；按重要程度排序，最多 5 项。每项含：
                    name(经历/项目名称)
                    desc(这段经历做了什么的概述)
                    role(本人担任的角色/职责)
                    techStack(在这段经历中用到的技能、工具、方法、专业知识数组，非技术岗位同样填写，例如会计可填["财务核算","Excel","用友"])
                    responsibilities(具体承担事项数组)
                    highlights(成果、亮点、难点或代表性产出数组)

                简历原文：
                %s
                """.formatted(rawText);
        return model.generate(List.of(UserMessage.from(prompt))).content().text();
    }
}
