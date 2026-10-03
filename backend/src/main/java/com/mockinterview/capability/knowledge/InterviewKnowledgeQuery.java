package com.mockinterview.capability.knowledge;

import com.mockinterview.agent.InterviewState;
import com.mockinterview.domain.ResumeStructured;

/** Keep the latest exchange first so a long resume does not dominate the retrieval. */
public final class InterviewKnowledgeQuery {
    private InterviewKnowledgeQuery() {}

    public static String question(ResumeStructured resume, InterviewState state, String history, String role) {
        StringBuilder query = new StringBuilder(tail(history, 240));
        if (resume.getProjects() != null && !resume.getProjects().isEmpty()) {
            int index = Math.max(0, Math.min(state.getCurrentProjectIdx(), resume.getProjects().size() - 1));
            var project = resume.getProjects().get(index);
            query.append("\n当前项目：").append(head(project.getName(), 50));
            if (project.getTechStack() != null) {
                query.append(" 技术：").append(head(String.join("、", project.getTechStack()), 120));
            }
        } else if (resume.getSkills() != null) {
            query.append("\n技能：").append(head(String.join("、", resume.getSkills()), 120));
        }
        query.append("\n岗位：").append(head(role, 40)).append(" 层级：").append(state.getLayer().label());
        return head(query.toString(), 480);
    }

    public static String assessment(String history, String answer) {
        return tail(history, 240) + "\n回答：" + head(answer, 230);
    }

    private static String head(String value, int limit) {
        if (value == null) return "";
        int end = Math.min(limit, value.length());
        if (end > 0 && Character.isHighSurrogate(value.charAt(end - 1))) end--;
        return value.substring(0, end);
    }

    private static String tail(String value, int limit) {
        if (value == null) return "";
        int start = Math.max(0, value.length() - limit);
        if (start < value.length() && Character.isLowSurrogate(value.charAt(start))) start++;
        return value.substring(start);
    }
}
