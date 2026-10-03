package com.mockinterview.service.interview;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.mockinterview.agent.*;
import com.mockinterview.capability.memory.LongTermMemoryService;
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
    private final LongTermMemoryService memoryService;
    private final com.mockinterview.service.speech.SpeechStore speech;

    public InterviewService(ResumeRepository resumeRepository,
                            InterviewSessionRepository sessionRepository,
                            InterviewMessageRepository messageRepository,
                            InterviewReportRepository reportRepository,
                            InterviewOrchestrator orchestrator,
                            DiagnosisAgent diagnosisAgent,
                            DiagnosisParser diagnosisParser,
                            ObjectMapper objectMapper,
                            SessionStateStore stateStore,
                            LongTermMemoryService memoryService,
                            com.mockinterview.service.speech.SpeechStore speech) {
        this.resumeRepository = resumeRepository;
        this.sessionRepository = sessionRepository;
        this.messageRepository = messageRepository;
        this.reportRepository = reportRepository;
        this.orchestrator = orchestrator;
        this.diagnosisAgent = diagnosisAgent;
        this.diagnosisParser = diagnosisParser;
        this.objectMapper = objectMapper;
        this.stateStore = stateStore;
        this.memoryService = memoryService;
        this.speech = speech;
    }

    public Long create(com.mockinterview.domain.dto.CreateInterviewRequest request) {
        Long resumeId = request.resumeId();
        Resume resume = resumeRepository.findById(resumeId)
                .orElseThrow(() -> new NotFoundException("简历不存在: " + resumeId));
        if (resume.getStatus() != ResumeStatus.PARSED) {
            throw new IllegalStateException("简历尚未解析完成");
        }
        String targetRole = request.targetRole() == null ? "" : request.targetRole().trim();
        if (targetRole.isEmpty()) {
            throw new IllegalStateException("请填写目标岗位方向");
        }
        ResumeStructured structured = loadStructured(resume);
        int totalProjects = structured.getProjects() == null ? 0 : structured.getProjects().size();

        InterviewSession session = InterviewSession.builder()
                .resumeId(resumeId)
                .targetRole(targetRole)
                .mode(request.mode() == null ? "TEXT" : request.mode())
                .jobDetail(buildJobDetail(request))
                .status(InterviewStatus.IN_PROGRESS)
                .stage(Stage.OPENING.name())
                .layer(Layer.L1_BACKGROUND.name())
                .currentProjectIdx(0)
                .totalProjects(totalProjects)
                .questionCount(0)
                .consecutiveStuck(0)
                .difficulty(2)
                .pressureUsed(false)
                .startedAt(LocalDateTime.now())
                .build();
        sessionRepository.save(session);
        return session.getId();
    }

    private String buildJobDetail(com.mockinterview.domain.dto.CreateInterviewRequest request) {
        StringBuilder sb = new StringBuilder();
        String resp = request.jobResponsibilities() == null ? "" : request.jobResponsibilities().trim();
        String req = request.jobRequirements() == null ? "" : request.jobRequirements().trim();
        if (!resp.isEmpty()) {
            sb.append("【岗位职责】\n").append(resp);
        }
        if (!req.isEmpty()) {
            if (!sb.isEmpty()) sb.append("\n\n");
            sb.append("【任职要求】\n").append(req);
        }
        return sb.toString();
    }

    public List<Map<String, Object>> list() {
        return sessionRepository.findAllByOrderByStartedAtDesc().stream().map(s -> {
            Resume r = resumeRepository.findById(s.getResumeId()).orElse(null);
            java.util.Map<String, Object> m = new java.util.LinkedHashMap<>();
            m.put("id", s.getId());
            m.put("resumeFilename", r == null || r.getFilename() == null ? "" : r.getFilename());
            m.put("status", s.getStatus() == null ? "" : s.getStatus().name());
            m.put("startedAt", s.getStartedAt() == null ? "" : s.getStartedAt().toString());
            return m;
        }).toList();
    }

    public Map<String, Object> resumeOf(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("会话不存在: " + sessionId));
        Resume resume = resumeRepository.findById(session.getResumeId())
                .orElseThrow(() -> new NotFoundException("简历不存在: " + session.getResumeId()));
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("resumeId", resume.getId());
        m.put("filename", resume.getFilename());
        m.put("rawText", resume.getRawText());
        m.put("structured", loadStructured(resume));
        return m;
    }

    public Map<String, Object> detail(Long sessionId) {
        InterviewSession s = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("会话不存在: " + sessionId));
        Map<String, Object> m = new java.util.LinkedHashMap<>();
        m.put("id", s.getId());
        m.put("targetRole", s.getTargetRole() == null ? "" : s.getTargetRole());
        m.put("mode", s.getMode() == null ? "TEXT" : s.getMode());
        m.put("status", s.getStatus() == null ? "" : s.getStatus().name());
        m.put("stage", s.getStage());
        m.put("startedAt", s.getStartedAt() == null ? "" : s.getStartedAt().toString());
        m.put("endedAt", s.getEndedAt() == null ? "" : s.getEndedAt().toString());
        return m;
    }

    @org.springframework.transaction.annotation.Transactional
    public void delete(Long sessionId) {
        InterviewSession session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new NotFoundException("会话不存在: " + sessionId));
        Long resumeId = session.getResumeId();
        speech.deleteInterview(sessionId);
        messageRepository.deleteBySessionId(sessionId);
        reportRepository.deleteBySessionId(sessionId);
        sessionRepository.deleteById(sessionId);
        if (resumeId != null && sessionRepository.countByResumeId(resumeId) == 0) {
            resumeRepository.deleteById(resumeId);
        }
    }

    @Async
    public void start(Long sessionId, SseEmitter emitter) {
        try {
            InterviewSession session = sessionRepository.findById(sessionId)
                    .orElseThrow(() -> new NotFoundException("会话不存在: " + sessionId));
            ResumeStructured resume = loadStructured(session.getResumeId());
            InterviewState state = loadState(session);
            String history = history(sessionId);
            TokenStream stream = orchestrator.nextQuestion(resume, state, history, sessionId,
                    session.getTargetRole(), session.getJobDetail());
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

            if (isExitIntent(content)) {
                InterviewState closing = state.toBuilder().stage(Stage.CLOSING).build();
                saveState(session, closing);
                emitter.send(SseEmitter.event().name("interview_end").data(""));
                emitter.complete();
                return;
            }

            String history = history(sessionId);

            AnswerAssessment assessment = orchestrator.assess(history, content);
            String lastQuestion = lastQuestion(sessionId);
            String feedback = orchestrator.feedback(session.getTargetRole(), lastQuestion, content, assessment);
            if (!feedback.isBlank()) {
                emitter.send(SseEmitter.event().name("feedback").data(feedback));
            }

            InterviewState next = orchestrator.nextState(state, assessment);
            saveState(session, next);

            if (next.getStage() == Stage.CLOSING) {
                emitter.send(SseEmitter.event().name("interview_end").data(""));
                emitter.complete();
                return;
            }

            ResumeStructured resume = loadStructured(session.getResumeId());
            TokenStream stream = orchestrator.nextQuestion(resume, next, history, sessionId,
                    session.getTargetRole(), session.getJobDetail());
            streamQuestion(sessionId, stream, next.getLayer(), next.getQuestionCount(), emitter);
        } catch (Exception e) {
            emitter.completeWithError(e);
        }
    }

    private static final java.util.List<String> EXIT_PATTERNS = java.util.List.of(
            "结束面试", "结束吧", "结束了", "不面了", "不想面", "别问了", "不想回答",
            "可以了", "够了", "我想结束", "就到这里", "到此为止", "stop interview",
            "结束本次", "问这些吧", "到此结束", "跳过吧", "我放弃");

    private boolean isExitIntent(String content) {
        if (content == null) return false;
        String t = content.trim().toLowerCase();
        if (t.length() > 30) return false;
        for (String p : EXIT_PATTERNS) {
            if (t.contains(p.toLowerCase())) return true;
        }
        return false;
    }

    public List<MessageDto> messages(Long sessionId) {
        return messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId).stream()
                .map(m -> new MessageDto(m.getRole().name(), m.getContent(), layerLabel(m.getLayer())))
                .toList();
    }

    private String layerLabel(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        try {
            return Layer.valueOf(name).label();
        } catch (IllegalArgumentException e) {
            return name;
        }
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
        String role = session.getTargetRole() == null ? "" : session.getTargetRole();
        String job = session.getJobDetail() == null ? "" : session.getJobDetail();
        String raw = diagnosisAgent.generateReport(role, job, resume.getParsedJson(), history(sessionId));
        DiagnosisReport report = diagnosisParser.parse(raw);
        if (report.scores().isEmpty() && report.weaknesses().isEmpty()) {
            throw new IllegalStateException("复盘报告生成失败，请稍后重试");
        }

        memoryService.rememberWeaknesses(report.weaknesses());

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
            List<String> weaknesses = objectMapper.readValue(r.getWeaknessesJson(), new TypeReference<>() {
            });
            List<String> suggestions = objectMapper.readValue(r.getSuggestionsJson(), new TypeReference<>() {
            });
            return new ReportResponse(scores, weaknesses, suggestions);
        } catch (Exception e) {
            return new ReportResponse(Map.<String, Object>of(), List.of(), List.of());
        }
    }

    // ---- 内部辅助 ----

    private void streamQuestion(Long sessionId, TokenStream stream, Layer layer, int questionIdx, SseEmitter emitter) {
        try {
            emitter.send(SseEmitter.event().name("layer").data(layer.label()));
        } catch (IOException ignored) {
        }
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
                    memoryService.rememberTopic(sessionId, topicFrom(full, layer));
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

    private String topicFrom(String question, Layer layer) {
        String trimmed = question.length() > 40 ? question.substring(0, 40) : question;
        return layer.label() + ":" + trimmed.replaceAll("\\s+", " ");
    }

    private String lastQuestion(Long sessionId) {
        List<InterviewMessage> msgs = messageRepository.findBySessionIdOrderByCreatedAtAsc(sessionId);
        for (int i = msgs.size() - 1; i >= 0; i--) {
            if (msgs.get(i).getRole() == MessageRole.INTERVIEWER) {
                return msgs.get(i).getContent();
            }
        }
        return "";
    }

    private InterviewState loadState(InterviewSession session) {
        return stateStore.loadState(session.getId()).orElseGet(() -> InterviewState.builder()
                .stage(Stage.valueOf(session.getStage()))
                .layer(Layer.valueOf(session.getLayer()))
                .currentProjectIdx(session.getCurrentProjectIdx())
                .totalProjects(session.getTotalProjects())
                .questionCount(session.getQuestionCount())
                .consecutiveStuck(session.getConsecutiveStuck())
                .difficulty(session.getDifficulty() == null ? 2 : session.getDifficulty())
                .pressureUsed(Boolean.TRUE.equals(session.getPressureUsed()))
                .build());
    }

    private void saveState(InterviewSession session, InterviewState state) {
        session.setStage(state.getStage().name());
        session.setLayer(state.getLayer().name());
        session.setCurrentProjectIdx(state.getCurrentProjectIdx());
        session.setTotalProjects(state.getTotalProjects());
        session.setQuestionCount(state.getQuestionCount());
        session.setConsecutiveStuck(state.getConsecutiveStuck());
        session.setDifficulty(state.getDifficulty());
        session.setPressureUsed(state.isPressureUsed());
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
        return loadStructured(resume);
    }
}
