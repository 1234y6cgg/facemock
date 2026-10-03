package com.mockinterview.config;

import com.mockinterview.capability.knowledge.*;
import com.mockinterview.service.question.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.*;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

@Component
@Order(100)
@EnableConfigurationProperties(QuestionProperties.class)
public class QuestionBootstrap implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(QuestionBootstrap.class);
    private final QuestionCatalogLoader loader;
    private final QuestionImportService importer;
    private final QuestionProperties properties;
    private final KnowledgeProperties knowledgeProperties;
    private final TechKnowledgeService knowledge;
    public QuestionBootstrap(QuestionCatalogLoader loader, QuestionImportService importer,
            QuestionProperties properties, KnowledgeProperties knowledgeProperties, TechKnowledgeService knowledge) {
        this.loader = loader; this.importer = importer; this.properties = properties;
        this.knowledgeProperties = knowledgeProperties; this.knowledge = knowledge;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.isSeedOnStartup()) return;
        var catalog = loader.loadBuiltin();
        var result = importer.importCatalog(catalog);
        log.info("题库导入：{} 道，新增版本 {}，未变化 {}",
                result.questionCount(), result.newRevisions(), result.unchangedRevisions());
        if (!properties.isIndexKnowledge() || !knowledgeProperties.isEnabled()) return;
        // Database transaction has committed before slow/native embedding and Chroma calls.
        try {
            for (var source : catalog.sources()) knowledge.upsert(source.document());
            log.info("题库参考知识索引完成：{} 篇", catalog.sources().size());
        } catch (KnowledgeUnavailableException e) {
            log.warn("题库已存入数据库，参考知识索引未完成；恢复 ChromaDB 后重启应用重试：{}", e.getMessage());
        }
    }
}
