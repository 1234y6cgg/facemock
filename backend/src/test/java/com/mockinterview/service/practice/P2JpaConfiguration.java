package com.mockinterview.service.practice;
import com.mockinterview.domain.practice.PracticeSession;
import com.mockinterview.domain.question.QuestionEntity;
import com.mockinterview.repository.practice.PracticeSessionRepository;
import com.mockinterview.repository.question.QuestionRepository;
import com.mockinterview.service.question.*;
import com.mockinterview.capability.evaluation.*;
import jakarta.validation.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@TestConfiguration
@EntityScan(basePackageClasses={PracticeSession.class,QuestionEntity.class})
@EnableJpaRepositories(basePackageClasses={PracticeSessionRepository.class,QuestionRepository.class})
@Import({QuestionImportService.class,QuestionService.class,QuestionJson.class,QuestionCatalogValidator.class,QuestionCatalogLoader.class,
    PracticeStore.class,PracticeReferences.class,PracticeEvaluationStore.class,PracticeEvaluationWorker.class,TrainingEvaluationParser.class,PracticeEvaluationEngine.class})
public class P2JpaConfiguration {
    @Bean(destroyMethod="close") ValidatorFactory validatorFactory() { return Validation.buildDefaultValidatorFactory(); }
    @Bean Validator validator(ValidatorFactory factory) { return factory.getValidator(); }
}
