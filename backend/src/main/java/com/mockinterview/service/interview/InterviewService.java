package com.mockinterview.service.interview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.agent.*;
import com.mockinterview.controller.NotFoundException;
import com.mockinterview.domain.*;
import com.mockinterview.domain.dto.MessageDto;
import com.mockinterview.domain.dto.ReportResponse;
import com.mockinterview.infrastructure.redis.SessionStateStore;
import com.mockinterview.repository.*;
import dev.langchain4j.service.TokenStream;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class InterviewService {

    private final ResumeRepository resumeRepository;
    private final InterviewSessionRepository sessionRepository;
    private final InterviewMessageRepository messageRepository;
    private final InterviewOrchestrator orchestrator;
    private final ObjectMapper objectMapper;
    private final SessionStateStore stateStore;
    private final DiagnosisAgent diagnosisAgent;
    private final DiagnosisParser diagnosisParser;
    private final InterviewReportRepository reportRepository;

    public InterviewService(ResumeRepository resumeRepository,
                            InterviewSessionRepository sessionRepository,
                            InterviewMessageRepository messageRepository,
                            InterviewReportRepository reportRepository,
                            InterviewOrchestrator orchestrator,
                            DiagnosisAgent diagnosisAgent,
                            DiagnosisParser diagnosisParser,
                            ObjectMapper objectMapper,
                            SessionStateStore stateStore) {
        this.resumeRepository = resumeRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.reportRepository = reportRepository;
        this.orchestrator = orchestrator;
        this.diagnosisAgent = diagnosisAgent;
        this.diagnosisParser = diagnosisParser;
        this.objectMapper = objectMapper;
        this.stateStore = stateStore;
    }

    public Long create(Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new NotFoundException("简历不存在: " + resumeId));
        if (resume.getStatus() != ResumeStatus.PARSED) {
            throw new IllegalStateException("简历尚未解析完成");
        }
        ResumeStructured structured = loadStructured(resume);
        int totalProjects = structured.getProjects() == null ? 0 : structured.getProjects().size();

        InterviewSession session = InterviewSession.builder()
                .resumeId(resumeId)
                .status(InterviewStatus.IN_PROGRESS)
                .stage(Stage.OPENING.name())
                .layer(Layer.L1_BACKGROUND.name())
                .currentProjectIdx(0)
                .totalProjects(totalProjects)
                .questionCount(0)
                .consecutiveStuck(0)
                .startedAt(LocalDateTime.now())
                .build();
        sessionRepository.save(session);
        return session.getId();
    }

    @Async
    public void start(Long sessionId, SseEmitter emitter) {
        try {
            InterviewSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new NotFoundException("会话不存在: " + sessionId));
            ResumeStructured resume = loadStructured(session.getResumeId());
            InterviewState state = loadState(session);
            String history = history(sessionId);
            TokenStream stream = orchestrator.generateQuestion(
                    resume, state.getCurrentProjectIdx(), state.getLayer(), history);
            streamQuestion(sessionId, stream, state.getLayer(), state.getQuestionCount(), emitter);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    @Async
    public void answer(Long sessionId, String content, SseEmitter emitter) {
        try {
            InterviewSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new NotFoundException("会话不存在: " + sessionId));
            InterviewState state = loadState(session);

            saveMessage(sessionId, MessageRole.CANDIDATE, content, state.getLayer().name(), state.getQuestionCount());
            String history = history(sessionId);

            AnswerAssessment assessment = orchestrator.assess(history, content);
            InterviewState next = orchestrator.nextState(state, assessment);
            saveState(session, next);

            if (next.getStage() == Stage.CLOSING) {
                emitter.send(SseEmitter.event().name("interview_end").data(""));
                emitter.complete();
                return;
            }

            ResumeStructured resume = loadStructured(session.getResumeId());
            TokenStream stream = orchestrator.generateQuestion(
                    resume, next.getCurrentProjectIdx(), next.getLayer(), history);
            streamQuestion(sessionId, stream, next.getLayer(), next.getQuestionCount(), emitter);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    public List<MessageDto> messages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(m -> new MessageDto(m.getRole().name(), m.getContent(), m.getLayer()))
                .toList();
    }

    public ReportResponse report(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("会话不存在: " + sessionId));
        if (session.getStatus() != InterviewStatus.COMPLETED) {
            throw new IllegalStateException("面试尚未结束");
        }
        Optional<InterviewReport> existing = reportRepository.findBySessionId(sessionId);
        if (existing.isPresent()) {
            return toResponse(existing.get());
        }

        Resume resume = resumeRepository.findById(session.getResumeId())
                .orElseThrow(() -> new NotFoundException("简历不存在: " + session.getResumeId()));
        String raw = diagnosisAgent.generateReport(resume.getParsedJson(), history(sessionId));
        DiagnosisReport report = diagnosisParser.parse(raw);

        try {
            InterviewReport entity = InterviewReport.builder()
                    .sessionId(sessionId)
                    .scoresJson(objectMapper.writeValueAsString(report.scores()))
                    .weaknessesJson(objectMapper.writeValueAsString(report.weaknesses()))
                    .suggestionsJson(objectMapper.writeValueAsString(report.suggestions()))
                    .createdAt(LocalDateTime.now())
                    .build();
            reportRepository.save(entity);
        } catch (Exception ignored) {
        }
        return new ReportResponse(report.scores(), report.weaknesses(), report.suggestions());
    }

    private ReportResponse toResponse(InterviewReport r) {
        try {
            Map<String, Object> scores = objectMapper.readValue(r.getScoresJson(), new TypeReference<>() {
            });
            java.util.List<String> weaknesses = objectMapper.readValue(r.getWeaknessesJson(), new TypeReference<>() {
            });
            java.util.List<String> suggestions = objectMapper.readValue(r.getSuggestionsJson(), new TypeReference<>() {
            });
            return new ReportResponse(scores, weaknesses, suggestions);
        } catch (Exception e) {
            return new ReportResponse(Map.of(), java.util.List.of(), java.util.List.of());
        }
    }

    // ---- 内部辅助 ----

    private void streamQuestion(Long sessionId, TokenStream stream, Layer layer, int questionIdx, SseEmitter emitter) {
        StringBuilder sb = new StringBuilder();
        stream.onNext(token -> {
                    sb.append(token);
                    try {
                        emitter.send(SseEmitter.event().name("message").data(token));
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onComplete(response -> {
                    String full = response.content() != null ? response.content().text() : sb.toString();
                    saveMessage(sessionId, MessageRole.INTERVIEWER, full, layer.name(), questionIdx);
                    try {
                        emitter.send(SseEmitter.event().name("done").data(""));
                        emitter.complete();
                    } catch (IOException e) {
                        emitter.completeWithError(e);
                    }
                })
                .onError(emitter::completeWithError)
                .start();
    }

    private InterviewState loadState(InterviewSession session) {
        return stateStore.loadState(session.getId()).orElseGet(() -> InterviewState.builder()
                .stage(Stage.valueOf(session.getStage()))
                .layer(Layer.valueOf(session.getLayer()))
                .currentProjectIdx(session.getCurrentProjectIdx())
                .totalProjects(session.getTotalProjects())
                .questionCount(session.getQuestionCount())
                .consecutiveStuck(session.getConsecutiveStuck())
                .build());
    }

    private void saveState(InterviewSession session, InterviewState state) {
        session.setStage(state.getStage().name());
        session.setLayer(state.getLayer().name());
        session.setCurrentProjectIdx(state.getCurrentProjectIdx());
        session.setTotalProjects(state.getTotalProjects());
        session.setQuestionCount(state.getQuestionCount());
        session.setConsecutiveStuck(state.getConsecutiveStuck());
        if (state.getStage() == Stage.CLOSING) {
            session.setStatus(InterviewStatus.COMPLETED);
            session.setEndedAt(LocalDateTime.now());
        }
        sessionRepository.save(session);
        stateStore.saveState(session.getId(), state);
    }

    private String history(Long sessionId) {
        List<InterviewMessage> msgs = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        StringBuilder sb = new StringBuilder();
        for (InterviewMessage m : msgs) {
            sb.append(m.getRole().name()).append(": ").append(m.getContent()).append("\n");
        }
        return sb.toString();
    }

    private void saveMessage(Long sessionId, MessageRole role, String content, String layer, Integer questionIdx) {
        messageRepository.save(InterviewMessage.builder()
                .sessionId(sessionId).role(role).content(content)
                .layer(layer).questionIdx(questionIdx)
                .createdAt(LocalDateTime.now()).build());
    }

    private ResumeStructured loadStructured(InterviewSession session) {
        return loadStructured(session.getResumeId());
    }

    private ResumeStructured loadStructured(Resume resume) {
        try {
            return objectMapper.readValue(resume.getParsedJson(), ResumeStructured.class);
        } catch (Exception e) {
            throw new IllegalStateException("简历结构化数据不可用", e);
        }
    }

    private ResumeStructured loadStructured(Long resumeId) {
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new NotFoundException("简历不存在: " + resumeId));
        try {
            return objectMapper.readValue(resume.getParsedJson(), ResumeStructured.class);
        } catch (Exception e) {
            throw new IllegalStateException("简历结构化数据不可用", e);
        }
    }
}
