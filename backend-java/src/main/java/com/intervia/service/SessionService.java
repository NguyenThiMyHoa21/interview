package com.intervia.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.intervia.config.ApiException;
import com.intervia.dto.CvDto.CvVersionView;
import com.intervia.dto.JdDto.JdVersionView;
import com.intervia.dto.RoleDto;
import com.intervia.dto.SessionDto.*;
import com.intervia.model.*;
import com.intervia.repository.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Slf4j
@Service
@RequiredArgsConstructor
public class SessionService {

    private final SessionRepository sessionRepository;
    private final BlueprintRepository blueprintRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final SessionEventRepository sessionEventRepository;
    private final SessionReportRepository sessionReportRepository;

    private final CvService cvService;
    private final JdService jdService;
    private final RoleService roleService;
    private final GeminiService geminiService;
    private final ObjectMapper objectMapper;

    public List<SessionSummary> listSessions(String userId) {
        List<SessionDocument> sessions = sessionRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        List<SessionSummary> list = new ArrayList<>();
        for (SessionDocument doc : sessions) {
            list.add(toSummary(doc, userId));
        }
        return list;
    }

    public List<SessionSummary> listSessionsByCvId(String userId, String cvId) {
        List<SessionDocument> sessions = sessionRepository.findAllByUserIdAndCvId(userId, cvId);
        List<SessionSummary> list = new ArrayList<>();
        for (SessionDocument doc : sessions) {
            list.add(toSummary(doc, userId));
        }
        return list;
    }

    public List<SessionSummary> listSessionsByJdId(String userId, String jdId) {
        List<SessionDocument> sessions = sessionRepository.findAllByUserIdAndJdId(userId, jdId);
        List<SessionSummary> list = new ArrayList<>();
        for (SessionDocument doc : sessions) {
            list.add(toSummary(doc, userId));
        }
        return list;
    }

    public SessionDocument requireSession(String userId, String sessionId) {
        return sessionRepository.findByIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND, "Không tìm thấy phiên phỏng vấn."));
    }

    public SessionSummary getSession(String userId, String sessionId) {
        return toSummary(requireSession(userId, sessionId), userId);
    }

    public SessionSummary createSession(String userId, CreateSessionRequest req) {
        RoleDto role = roleService.findRoleOrDefault(req.getRoleId(), req.getRoleCustom());
        String level = (req.getLevel() != null && !req.getLevel().isBlank()) ? req.getLevel() : "junior";

        // Get latest CV and JD versions if not specified
        Integer cvVer = null;
        if (req.getCvId() != null) {
            CvDocument cv = cvService.requireCv(userId, req.getCvId());
            cvVer = cv.getLatestVersion();
        }
        Integer jdVer = null;
        if (req.getJdId() != null) {
            JdDocument jd = jdService.requireJd(userId, req.getJdId());
            jdVer = jd.getLatestVersion();
        }

        String sessionId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        SessionDocument session = SessionDocument.builder()
                .id(sessionId)
                .userId(userId)
                .role(role)
                .level(level)
                .status("draft")
                .mode("text")
                .cvId(req.getCvId())
                .cvVersion(cvVer)
                .jdId(req.getJdId())
                .jdVersion(jdVer)
                .hasBlueprint(false)
                .turnInProgress(false)
                .seq(0)
                .askedCount(0)
                .plannedCount(0)
                .deleting(false)
                .createdAt(now)
                .schemaVersion(1)
                .build();
        sessionRepository.save(session);

        return toSummary(session, userId);
    }

    public SessionSummary updateDraftInputs(String userId, String sessionId, Integer cvVersion, Integer jdVersion) {
        SessionDocument session = requireSession(userId, sessionId);
        if (!"draft".equals(session.getStatus())) {
            throw new ApiException("session_locked", HttpStatus.CONFLICT, "Phiên đã bắt đầu, không thay đổi được phiên bản CV/JD.");
        }
        if (cvVersion != null) session.setCvVersion(cvVersion);
        if (jdVersion != null) session.setJdVersion(jdVersion);
        sessionRepository.save(session);
        return toSummary(session, userId);
    }

    public boolean deleteSession(String userId, String sessionId) {
        SessionDocument session = requireSession(userId, sessionId);
        sessionRepository.delete(session);
        blueprintRepository.deleteBySessionId(sessionId);
        questionRepository.deleteBySessionId(sessionId);
        answerRepository.deleteBySessionId(sessionId);
        sessionEventRepository.deleteBySessionId(sessionId);
        sessionReportRepository.deleteBySessionId(sessionId);
        return true;
    }

    public BlueprintView triggerBlueprint(String userId, String sessionId) {
        SessionDocument session = requireSession(userId, sessionId);

        String cvText = "CV Ứng viên";
        if (session.getCvId() != null && session.getCvVersion() != null) {
            try {
                CvVersionView cvVer = cvService.getCvVersion(userId, session.getCvId(), String.valueOf(session.getCvVersion()));
                cvText = objectMapper.writeValueAsString(cvVer.getProfile());
            } catch (Exception ignored) {}
        }

        String jdText = "Mô tả công việc";
        if (session.getJdId() != null && session.getJdVersion() != null) {
            try {
                JdVersionView jdVer = jdService.getJdVersion(userId, session.getJdId(), String.valueOf(session.getJdVersion()));
                jdText = objectMapper.writeValueAsString(jdVer.getRequirements());
            } catch (Exception ignored) {}
        }

        List<BlueprintCompetency> competencies = geminiService.generateBlueprint(
                cvText, jdText,
                session.getRole() != null ? session.getRole().getName() : "Software Engineer",
                session.getLevel() != null ? session.getLevel() : "junior"
        );

        int totalQuestions = competencies.stream().mapToInt(BlueprintCompetency::getQuestionsMax).sum();

        BlueprintDocument bp = blueprintRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElse(BlueprintDocument.builder()
                        .id(UUID.randomUUID().toString())
                        .sessionId(sessionId)
                        .userId(userId)
                        .createdAt(Instant.now())
                        .schemaVersion(1)
                        .build());

        bp.setStatus("done");
        bp.setCvVersion(session.getCvVersion());
        bp.setJdVersion(session.getJdVersion());
        bp.setTotalQuestions(totalQuestions);
        bp.setCompetencies(competencies);
        bp.setUpdatedAt(Instant.now());
        blueprintRepository.save(bp);

        session.setHasBlueprint(true);
        session.setPlannedCount(totalQuestions);
        sessionRepository.save(session);

        return new BlueprintView("done", false, totalQuestions, competencies, null);
    }

    public BlueprintView getBlueprint(String userId, String sessionId) {
        requireSession(userId, sessionId);
        Optional<BlueprintDocument> bpOpt = blueprintRepository.findBySessionIdAndUserId(sessionId, userId);
        if (bpOpt.isEmpty()) {
            return new BlueprintView("pending", false, 0, new ArrayList<>(), null);
        }
        BlueprintDocument bp = bpOpt.get();
        return new BlueprintView(
                bp.getStatus() != null ? bp.getStatus() : "done",
                false,
                bp.getTotalQuestions() != null ? bp.getTotalQuestions() : 0,
                bp.getCompetencies() != null ? bp.getCompetencies() : new ArrayList<>(),
                bp.getError()
        );
    }

    public BlueprintView patchBlueprint(String userId, String sessionId, List<Map<String, Object>> compMaps) {
        SessionDocument session = requireSession(userId, sessionId);
        if (!"draft".equals(session.getStatus())) {
            throw new ApiException("session_locked", HttpStatus.CONFLICT, "Phiên đã bắt đầu, không thể sửa kế hoạch.");
        }
        BlueprintDocument bp = blueprintRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND, "Chưa tạo blueprint cho phiên này."));

        List<BlueprintCompetency> list = new ArrayList<>();
        for (Map<String, Object> map : compMaps) {
            BlueprintCompetency c = objectMapper.convertValue(map, BlueprintCompetency.class);
            list.add(c);
        }
        bp.setCompetencies(list);
        int total = list.stream().mapToInt(BlueprintCompetency::getQuestionsMax).sum();
        bp.setTotalQuestions(total);
        bp.setUpdatedAt(Instant.now());
        blueprintRepository.save(bp);

        session.setPlannedCount(total);
        sessionRepository.save(session);

        return new BlueprintView("done", false, total, list, null);
    }

    public SseEmitter startInterview(String userId, String sessionId, String mode) {
        SessionDocument session = requireSession(userId, sessionId);
        session.setStatus("in_progress");
        session.setMode(mode != null && !mode.isBlank() ? mode : "text");
        if (session.getStartedAt() == null) {
            session.setStartedAt(Instant.now());
        }
        sessionRepository.save(session);

        SseEmitter emitter = new SseEmitter(180000L);
        CompletableFuture.runAsync(() -> {
            try {
                List<QuestionDocument> existingQ = questionRepository.findAllBySessionIdOrderBySeqAsc(sessionId);
                QuestionDocument qDoc;
                String qText;
                if (!existingQ.isEmpty()) {
                    qDoc = existingQ.get(0);
                    qText = qDoc.getText();
                } else {
                    // Get first question from blueprint
                    BlueprintView bp = getBlueprint(userId, sessionId);
                    String compName = "Kiến thức nền tảng và giới thiệu";
                    if (!bp.getCompetencies().isEmpty()) {
                        compName = bp.getCompetencies().get(0).getName();
                    }

                    String roleName = session.getRole() != null ? session.getRole().getName() : "Software Engineer";
                    qText = geminiService.generateNextQuestion(roleName, session.getLevel(), compName, "");

                    qDoc = QuestionDocument.builder()
                            .id(UUID.randomUUID().toString())
                            .sessionId(sessionId)
                            .userId(userId)
                            .seq(1)
                            .index(1)
                            .text(qText)
                            .competency(new CompetencyRef("comp-1", compName))
                            .depth("L1")
                            .unplanned(false)
                            .createdAt(Instant.now())
                            .schemaVersion(1)
                            .build();
                    questionRepository.save(qDoc);

                    session.setAskedCount(1);
                    session.setSeq(1);
                    sessionRepository.save(session);
                }

                // 1. message_start
                sendSse(emitter, "message_start", Map.of(
                        "item_id", qDoc.getId(),
                        "kind", "question"
                ));

                // 2. token
                sendSse(emitter, "token", Map.of("text", qText));

                // 3. message_done
                Map<String, Object> qItem = new LinkedHashMap<>();
                qItem.put("kind", "question");
                qItem.put("id", qDoc.getId());
                qItem.put("index", qDoc.getIndex());
                qItem.put("text", qDoc.getText());
                qItem.put("competency", qDoc.getCompetency());
                qItem.put("depth", qDoc.getDepth());
                qItem.put("unplanned", qDoc.getUnplanned() != null ? qDoc.getUnplanned() : false);
                qItem.put("created_at", qDoc.getCreatedAt().toString());

                sendSse(emitter, "message_done", Map.of("item", qItem));

                // 4. progress
                int planned = session.getPlannedCount() != null && session.getPlannedCount() > 0 ? session.getPlannedCount() : 8;
                sendSse(emitter, "progress", Map.of("asked", 1, "planned", planned));

                emitter.complete();
            } catch (Exception e) {
                log.error("SSE start interview error: {}", e.getMessage(), e);
                sendSse(emitter, "error", Map.of("code", "internal_error", "message", e.getMessage() != null ? e.getMessage() : "Lỗi bắt đầu phỏng vấn"));
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    public SseEmitter submitTurn(String userId, String sessionId, String userText) {
        SessionDocument session = requireSession(userId, sessionId);
        SseEmitter emitter = new SseEmitter(180000L);

        CompletableFuture.runAsync(() -> {
            try {
                // Find current question
                QuestionDocument currentQ = questionRepository.findFirstBySessionIdOrderBySeqDesc(sessionId).orElse(null);
                String qId = currentQ != null ? currentQ.getId() : UUID.randomUUID().toString();

                int currentSeq = (session.getSeq() != null ? session.getSeq() : 1) + 1;

                // Save user answer
                AnswerDocument answer = AnswerDocument.builder()
                        .id(UUID.randomUUID().toString())
                        .sessionId(sessionId)
                        .userId(userId)
                        .seq(currentSeq)
                        .questionId(qId)
                        .textOriginal(userText)
                        .textEdited(null)
                        .source("text")
                        .hasAudio(false)
                        .createdAt(Instant.now())
                        .schemaVersion(1)
                        .build();
                answerRepository.save(answer);

                Map<String, Object> userItem = new LinkedHashMap<>();
                userItem.put("kind", "answer");
                userItem.put("id", answer.getId());
                userItem.put("question_id", qId);
                userItem.put("text_original", userText);
                userItem.put("text_edited", null);
                userItem.put("source", "text");
                userItem.put("has_audio", false);
                userItem.put("duration_ms", null);
                userItem.put("created_at", answer.getCreatedAt().toString());

                // user_item event
                sendSse(emitter, "user_item", Map.of("item", userItem));

                String history = String.format("Q%d: %s\nA%d: %s",
                        session.getAskedCount() != null ? session.getAskedCount() : 1,
                        currentQ != null ? currentQ.getText() : "",
                        session.getAskedCount() != null ? session.getAskedCount() : 1,
                        userText);
                generateAndSendNextQuestion(emitter, userId, sessionId, session, currentQ, currentSeq, history);
            } catch (Exception e) {
                log.error("SSE submitTurn error: {}", e.getMessage(), e);
                sendSse(emitter, "error", Map.of("code", "internal_error", "message", e.getMessage() != null ? e.getMessage() : "Lỗi xử lý câu trả lời"));
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private void generateAndSendNextQuestion(
            SseEmitter emitter,
            String userId,
            String sessionId,
            SessionDocument session,
            QuestionDocument currentQ,
            int currentSeq,
            String history
    ) {
        int asked = session.getAskedCount() != null ? session.getAskedCount() : 1;
        BlueprintView bp = getBlueprint(userId, sessionId);
        int planned = bp.getTotalQuestions() > 0 ? bp.getTotalQuestions() : 8;

        if (asked >= planned) {
            session.setStatus("completed");
            session.setEndedAt(Instant.now());
            sessionRepository.save(session);

            SessionEndSummary endSummary = getSessionEndSummary(session);
            sendSse(emitter, "session_ended", endSummary);
            emitter.complete();
            return;
        }

        // Next question
        int nextIdx = asked + 1;
        String compName = "Kỹ năng chuyên môn";
        if (!bp.getCompetencies().isEmpty()) {
            int compIdx = Math.min((nextIdx - 1) % bp.getCompetencies().size(), bp.getCompetencies().size() - 1);
            compName = bp.getCompetencies().get(compIdx).getName();
        }

        String roleName = session.getRole() != null ? session.getRole().getName() : "Software Engineer";
        String nextQText = geminiService.generateNextQuestion(roleName, session.getLevel(), compName, history);

        QuestionDocument nextQ = QuestionDocument.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .userId(userId)
                .seq(currentSeq + 1)
                .index(nextIdx)
                .text(nextQText)
                .competency(new CompetencyRef("comp-" + nextIdx, compName))
                .depth("L2")
                .unplanned(false)
                .createdAt(Instant.now())
                .schemaVersion(1)
                .build();
        questionRepository.save(nextQ);

        session.setAskedCount(nextIdx);
        session.setSeq(currentSeq + 1);
        sessionRepository.save(session);

        // message_start
        sendSse(emitter, "message_start", Map.of(
                "item_id", nextQ.getId(),
                "kind", "question"
        ));
        // token
        sendSse(emitter, "token", Map.of("text", nextQText));
        // message_done
        Map<String, Object> nextQItem = new LinkedHashMap<>();
        nextQItem.put("kind", "question");
        nextQItem.put("id", nextQ.getId());
        nextQItem.put("index", nextQ.getIndex());
        nextQItem.put("text", nextQ.getText());
        nextQItem.put("competency", nextQ.getCompetency());
        nextQItem.put("depth", nextQ.getDepth());
        nextQItem.put("unplanned", false);
        nextQItem.put("created_at", nextQ.getCreatedAt().toString());

        sendSse(emitter, "message_done", Map.of("item", nextQItem));
        // progress
        sendSse(emitter, "progress", Map.of("asked", nextIdx, "planned", planned));

        emitter.complete();
    }

    public SseEmitter submitEvent(String userId, String sessionId, String type) {
        SessionDocument session = requireSession(userId, sessionId);
        SseEmitter emitter = new SseEmitter(180000L);

        CompletableFuture.runAsync(() -> {
            try {
                QuestionDocument currentQ = questionRepository.findFirstBySessionIdOrderBySeqDesc(sessionId).orElse(null);
                String qText = currentQ != null ? currentQ.getText() : "";
                String qId = currentQ != null ? currentQ.getId() : null;

                String normType = type != null ? type.toUpperCase() : "REPEAT";
                Integer hintLevel = "HINT".equals(normType) ? 1 : null;
                String spokenText = null;

                if ("CLARIFY".equals(normType)) {
                    spokenText = geminiService.generateClarification(qText);
                } else if ("HINT".equals(normType)) {
                    spokenText = geminiService.generateHint(qText, 1);
                } else if ("REPEAT".equals(normType)) {
                    spokenText = qText;
                } else if ("SKIP".equals(normType)) {
                    spokenText = null;
                }

                SessionEventDocument ev = saveEvent(userId, sessionId, qId, normType, hintLevel, spokenText);

                Map<String, Object> eventItem = new LinkedHashMap<>();
                eventItem.put("kind", "event");
                eventItem.put("id", ev.getId());
                eventItem.put("question_id", qId);
                eventItem.put("type", normType);
                eventItem.put("hint_level", hintLevel);
                eventItem.put("text", spokenText);
                eventItem.put("created_at", ev.getCreatedAt().toString());

                sendSse(emitter, "user_item", Map.of("item", eventItem));

                if (spokenText != null) {
                    sendSse(emitter, "message_start", Map.of("item_id", ev.getId(), "kind", "event", "event_type", normType));
                    sendSse(emitter, "token", Map.of("text", spokenText));
                    sendSse(emitter, "message_done", Map.of("item", eventItem));
                    emitter.complete();
                } else if ("SKIP".equals(normType)) {
                    int currentSeq = (session.getSeq() != null ? session.getSeq() : 1) + 1;
                    int asked = session.getAskedCount() != null ? session.getAskedCount() : 1;
                    String history = String.format("Q%d: %s\n(Ứng viên đã bỏ qua câu hỏi này)",
                            asked, currentQ != null ? currentQ.getText() : "");
                    generateAndSendNextQuestion(emitter, userId, sessionId, session, currentQ, currentSeq, history);
                } else {
                    emitter.complete();
                }
            } catch (Exception e) {
                log.error("SSE submitEvent error: {}", e.getMessage(), e);
                sendSse(emitter, "error", Map.of("code", "internal_error", "message", e.getMessage() != null ? e.getMessage() : "Lỗi xử lý sự kiện"));
                emitter.completeWithError(e);
            }
        });
        return emitter;
    }

    private SessionEventDocument saveEvent(String userId, String sessionId, String qId, String type, Integer hintLevel, String text) {
        SessionEventDocument ev = SessionEventDocument.builder()
                .id(UUID.randomUUID().toString())
                .sessionId(sessionId)
                .userId(userId)
                .questionId(qId)
                .type(type)
                .hintLevel(hintLevel)
                .text(text)
                .createdAt(Instant.now())
                .schemaVersion(1)
                .build();
        return sessionEventRepository.save(ev);
    }

    public TranscriptResponse getTranscript(String userId, String sessionId) {
        SessionDocument session = requireSession(userId, sessionId);

        List<QuestionDocument> questions = questionRepository.findAllBySessionIdOrderBySeqAsc(sessionId);
        if ("in_progress".equals(session.getStatus()) && questions.isEmpty()) {
            BlueprintView bp = getBlueprint(userId, sessionId);
            String compName = "Kiến thức nền tảng và giới thiệu";
            if (!bp.getCompetencies().isEmpty()) {
                compName = bp.getCompetencies().get(0).getName();
            }
            String roleName = session.getRole() != null ? session.getRole().getName() : "Software Engineer";
            String qText = geminiService.generateNextQuestion(roleName, session.getLevel(), compName, "");
            QuestionDocument qDoc = QuestionDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .sessionId(sessionId)
                    .userId(userId)
                    .seq(1)
                    .index(1)
                    .text(qText)
                    .competency(new CompetencyRef("comp-1", compName))
                    .depth("L1")
                    .unplanned(false)
                    .createdAt(Instant.now())
                    .schemaVersion(1)
                    .build();
            questionRepository.save(qDoc);
            questions.add(qDoc);
            session.setAskedCount(1);
            session.setSeq(1);
            sessionRepository.save(session);
        }

        List<AnswerDocument> answers = answerRepository.findAllBySessionIdOrderBySeqAsc(sessionId);
        List<SessionEventDocument> events = sessionEventRepository.findAllBySessionIdOrderByCreatedAtAsc(sessionId);

        // Check if interviewer owes a question (e.g. after candidate answer or SKIP):
        if ("in_progress".equals(session.getStatus()) && !questions.isEmpty()) {
            Instant lastQuestionTime = questions.get(questions.size() - 1).getCreatedAt();
            boolean needsNextQuestion = false;
            String history = "";
            int asked = session.getAskedCount() != null ? session.getAskedCount() : questions.size();
            QuestionDocument lastQ = questions.get(questions.size() - 1);

            for (AnswerDocument a : answers) {
                if (a.getCreatedAt().isAfter(lastQuestionTime)) {
                    needsNextQuestion = true;
                    history = String.format("Q%d: %s\nA%d: %s", asked, lastQ != null ? lastQ.getText() : "", asked, a.getTextOriginal());
                }
            }
            for (SessionEventDocument e : events) {
                if ("SKIP".equals(e.getType()) && e.getCreatedAt().isAfter(lastQuestionTime)) {
                    needsNextQuestion = true;
                    history = String.format("Q%d: %s\n(Ứng viên đã bỏ qua câu hỏi này)", asked, lastQ != null ? lastQ.getText() : "");
                }
            }

            if (needsNextQuestion) {
                BlueprintView bp = getBlueprint(userId, sessionId);
                int planned = bp.getTotalQuestions() > 0 ? bp.getTotalQuestions() : 8;
                if (asked >= planned) {
                    session.setStatus("completed");
                    session.setEndedAt(Instant.now());
                    sessionRepository.save(session);
                } else {
                    int nextIdx = asked + 1;
                    String compName = "Kỹ năng chuyên môn";
                    if (!bp.getCompetencies().isEmpty()) {
                        int compIdx = Math.min((nextIdx - 1) % bp.getCompetencies().size(), bp.getCompetencies().size() - 1);
                        compName = bp.getCompetencies().get(compIdx).getName();
                    }
                    String roleName = session.getRole() != null ? session.getRole().getName() : "Software Engineer";
                    String nextQText = geminiService.generateNextQuestion(roleName, session.getLevel(), compName, history);
                    int currentSeq = (session.getSeq() != null ? session.getSeq() : 1) + 1;
                    QuestionDocument nextQ = QuestionDocument.builder()
                            .id(UUID.randomUUID().toString())
                            .sessionId(sessionId)
                            .userId(userId)
                            .seq(currentSeq)
                            .index(nextIdx)
                            .text(nextQText)
                            .competency(new CompetencyRef("comp-" + nextIdx, compName))
                            .depth("L2")
                            .unplanned(false)
                            .createdAt(Instant.now())
                            .schemaVersion(1)
                            .build();
                    questionRepository.save(nextQ);
                    questions.add(nextQ);

                    session.setAskedCount(nextIdx);
                    session.setSeq(currentSeq);
                    sessionRepository.save(session);
                }
            }
        }

        SessionSummary summary = toSummary(session, userId);

        List<Map<String, Object>> items = new ArrayList<>();
        for (QuestionDocument q : questions) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kind", "question");
            map.put("id", q.getId());
            map.put("index", q.getIndex());
            map.put("text", q.getText());
            map.put("competency", q.getCompetency());
            map.put("depth", q.getDepth());
            map.put("unplanned", q.getUnplanned() != null ? q.getUnplanned() : false);
            map.put("created_at", q.getCreatedAt().toString());
            map.put("_time", q.getCreatedAt());
            items.add(map);
        }
        for (AnswerDocument a : answers) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kind", "answer");
            map.put("id", a.getId());
            map.put("question_id", a.getQuestionId());
            map.put("text_original", a.getTextOriginal());
            map.put("text_edited", a.getTextEdited());
            map.put("source", a.getSource() != null ? a.getSource() : "text");
            map.put("has_audio", a.getHasAudio() != null ? a.getHasAudio() : false);
            map.put("duration_ms", a.getDurationMs());
            map.put("created_at", a.getCreatedAt().toString());
            map.put("_time", a.getCreatedAt());
            items.add(map);
        }
        for (SessionEventDocument e : events) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("kind", "event");
            map.put("id", e.getId());
            map.put("question_id", e.getQuestionId());
            map.put("type", e.getType());
            map.put("hint_level", e.getHintLevel());
            map.put("text", e.getText());
            map.put("created_at", e.getCreatedAt().toString());
            map.put("_time", e.getCreatedAt());
            items.add(map);
        }

        items.sort(Comparator.comparing(m -> (Instant) m.getOrDefault("_time", Instant.EPOCH)));
        for (Map<String, Object> m : items) {
            m.remove("_time");
        }

        SessionEndSummary endSummary = "completed".equals(session.getStatus()) ? getSessionEndSummary(session) : null;
        return new TranscriptResponse(summary, items, endSummary);
    }

    public SessionEndSummary getSessionEndSummary(SessionDocument session) {
        if (session.getSummary() != null) {
            return session.getSummary();
        }
        List<QuestionDocument> questions = questionRepository.findAllBySessionIdOrderBySeqAsc(session.getId());
        int qCount = questions.size();
        int durationSeconds = 0;
        if (session.getStartedAt() != null) {
            Instant end = session.getEndedAt() != null ? session.getEndedAt() : Instant.now();
            durationSeconds = (int) Duration.between(session.getStartedAt(), end).getSeconds();
        }
        BlueprintView bp = getBlueprint(session.getUserId(), session.getId());
        List<CompetencyRef> assessed = new ArrayList<>();
        for (BlueprintCompetency c : bp.getCompetencies()) {
            assessed.add(new CompetencyRef(c.getId(), c.getName()));
        }
        return new SessionEndSummary(qCount, durationSeconds, assessed, new ArrayList<>());
    }

    public SessionEndSummary stopSession(String userId, String sessionId) {
        SessionDocument session = requireSession(userId, sessionId);
        if ("completed".equals(session.getStatus()) && session.getSummary() != null) {
            return session.getSummary();
        }

        SessionEndSummary summary = getSessionEndSummary(session);

        session.setStatus("completed");
        session.setEndedAt(Instant.now());
        session.setSummary(summary);
        sessionRepository.save(session);

        saveEvent(userId, sessionId, null, "STOP", null, "Phiên phỏng vấn kết thúc.");

        return summary;
    }

    public SessionSummary changeMode(String userId, String sessionId, String mode) {
        SessionDocument session = requireSession(userId, sessionId);
        if ("completed".equals(session.getStatus())) {
            throw new ApiException("session_locked", HttpStatus.CONFLICT, "Phiên đã kết thúc, không đổi được chế độ.");
        }
        session.setMode(mode != null && !mode.isBlank() ? mode : "text");
        sessionRepository.save(session);
        return toSummary(session, userId);
    }

    public List<Coverage> computeCoverage(String userId, String sessionId) {
        BlueprintView bp = getBlueprint(userId, sessionId);
        List<QuestionDocument> questions = questionRepository.findAllBySessionIdOrderBySeqAsc(sessionId);

        List<Coverage> result = new ArrayList<>();
        for (BlueprintCompetency c : bp.getCompetencies()) {
            int count = (int) questions.stream().filter(q -> q.getCompetency() != null && c.getName().equals(q.getCompetency().getName())).count();
            result.add(new Coverage(new CompetencyRef(c.getId(), c.getName()), count, c.getQuestionsMax()));
        }
        return result;
    }

    private void sendSse(SseEmitter emitter, String eventName, Object data) {
        try {
            String json = (data instanceof String) ? (String) data : objectMapper.writeValueAsString(data);
            emitter.send(SseEmitter.event().name(eventName).data(json));
        } catch (Exception e) {
            log.warn("Error sending SSE event {}: {}", eventName, e.getMessage());
        }
    }

    private SessionSummary toSummary(SessionDocument doc, String userId) {
        String cvName = "CV";
        if (doc.getCvId() != null) {
            try {
                cvName = cvService.requireCv(userId, doc.getCvId()).getName();
            } catch (Exception ignored) {}
        }
        String jdName = "JD";
        if (doc.getJdId() != null) {
            try {
                jdName = jdService.requireJd(userId, doc.getJdId()).getName();
            } catch (Exception ignored) {}
        }

        EntityRef cvRef = new EntityRef(doc.getCvId(), cvName, doc.getCvVersion());
        EntityRef jdRef = new EntityRef(doc.getJdId(), jdName, doc.getJdVersion());
        SessionProgress prog = new SessionProgress(
                doc.getAskedCount() != null ? doc.getAskedCount() : 0,
                doc.getPlannedCount() != null ? doc.getPlannedCount() : 0
        );

        String step = "match";
        if ("completed".equals(doc.getStatus())) {
            step = "review";
        } else if ("in_progress".equals(doc.getStatus())) {
            step = "interview";
        } else if (Boolean.TRUE.equals(doc.getHasBlueprint())) {
            step = "plan";
        }

        return new SessionSummary(
                doc.getId(),
                doc.getRole(),
                doc.getLevel() != null ? doc.getLevel() : "junior",
                doc.getStatus() != null ? doc.getStatus() : "draft",
                step,
                doc.getMode() != null ? doc.getMode() : "text",
                cvRef,
                jdRef,
                prog,
                doc.getCreatedAt(),
                doc.getStartedAt(),
                doc.getEndedAt()
        );
    }
}
