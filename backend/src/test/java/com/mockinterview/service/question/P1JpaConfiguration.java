package com.mockinterview.service.question;

import com.mockinterview.domain.question.QuestionEntity;
import com.mockinterview.repository.question.QuestionRepository;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.boot.test.context.TestConfiguration;

@TestConfiguration
@EntityScan(basePackageClasses = QuestionEntity.class)
@EnableJpaRepositories(basePackageClasses = QuestionRepository.class)
@Import({QuestionImportService.class, QuestionService.class, QuestionJson.class,
        QuestionCatalogValidator.class, QuestionCatalogLoader.class})
public class P1JpaConfiguration {
    @Bean(destroyMethod = "close")
    ValidatorFactory validatorFactory() { return Validation.buildDefaultValidatorFactory(); }
    @Bean
    Validator validator(ValidatorFactory factory) { return factory.getValidator(); }
}
