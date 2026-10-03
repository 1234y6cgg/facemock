package com.mockinterview.config;

import com.mockinterview.capability.knowledge.KnowledgeUnavailableException;
import com.mockinterview.capability.knowledge.TechKnowledgeService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class KnowledgeBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(KnowledgeBootstrap.class);
    private final KnowledgeProperties properties;
    private final TechKnowledgeService knowledge;

    public KnowledgeBootstrap(KnowledgeProperties properties, TechKnowledgeService knowledge) {
        this.properties = properties;
        this.knowledge = knowledge;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isEnabled() || !properties.isSeedOnStartup()) return;
        try {
            knowledge.seedBuiltin();
        } catch (KnowledgeUnavailableException e) {
            log.warn("启动时知识库导入失败，下一次检索会重试：{}", e.getMessage());
        }
    }
}
