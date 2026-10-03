package com.mockinterview.service.speech;
import com.mockinterview.domain.practice.PracticeSession;
import com.mockinterview.domain.question.QuestionEntity;
import com.mockinterview.domain.speech.SpeechRecording;
import com.mockinterview.repository.practice.PracticeSessionRepository;
import com.mockinterview.repository.question.QuestionRepository;
import com.mockinterview.repository.speech.SpeechRecordingRepository;
import com.mockinterview.service.question.*;
import com.mockinterview.service.practice.*;
import com.mockinterview.capability.evaluation.*;
import com.mockinterview.capability.speech.*;
import com.mockinterview.config.SpeechProperties;
import jakarta.validation.*;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;

@TestConfiguration
@EntityScan(basePackageClasses={PracticeSession.class,QuestionEntity.class,SpeechRecording.class,com.mockinterview.domain.InterviewSession.class})
@EnableJpaRepositories(basePackageClasses=com.mockinterview.repository.InterviewSessionRepository.class)
@Import({QuestionImportService.class,QuestionService.class,QuestionJson.class,QuestionCatalogValidator.class,QuestionCatalogLoader.class,
    PracticeStore.class,PracticeReferences.class,PracticeEvaluationStore.class,PracticeEvaluationWorker.class,TrainingEvaluationParser.class,PracticeEvaluationEngine.class,
    SpeechStore.class,SpeechProperties.class,SpeechFileStore.class,TranscriptionStore.class,TranscriptionWorker.class,PracticeDeletion.class})
public class P3JpaConfiguration {
    @Bean(destroyMethod="close") ValidatorFactory validatorFactory(){return Validation.buildDefaultValidatorFactory();}
    @Bean Validator validator(ValidatorFactory factory){return factory.getValidator();}
}
