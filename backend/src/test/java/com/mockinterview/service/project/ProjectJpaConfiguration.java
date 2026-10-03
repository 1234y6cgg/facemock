package com.mockinterview.service.project;
import com.mockinterview.domain.Resume;
import com.mockinterview.domain.project.TrainingProject;
import com.mockinterview.repository.ResumeRepository;
import com.mockinterview.repository.project.TrainingProjectRepository;
import com.mockinterview.service.question.QuestionJson;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.context.annotation.Import;

@TestConfiguration @EntityScan(basePackageClasses=Resume.class)
@EnableJpaRepositories(basePackageClasses=ResumeRepository.class)
@Import({ProjectStore.class,ProjectIndex.class,ProjectPractice.class,ProjectEvaluationStore.class,ProjectEvaluationWorker.class,ProjectAssessmentParser.class,QuestionJson.class})
public class ProjectJpaConfiguration {}
