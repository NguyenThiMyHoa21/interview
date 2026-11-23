package com.intervia.service;

import com.intervia.config.ApiException;
import com.intervia.dto.ReportDto.*;
import com.intervia.dto.SessionDto.BlueprintCompetency;
import com.intervia.dto.SessionDto.BlueprintView;
import com.intervia.model.AnswerDocument;
import com.intervia.model.QuestionDocument;
import com.intervia.model.SessionDocument;
import com.intervia.model.SessionReportDocument;
import com.intervia.repository.AnswerRepository;
import com.intervia.repository.QuestionRepository;
import com.intervia.repository.SessionReportRepository;
import com.intervia.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ReportService {

    private final SessionReportRepository reportRepository;
    private final SessionRepository sessionRepository;
    private final QuestionRepository questionRepository;
    private final AnswerRepository answerRepository;
    private final SessionService sessionService;
    private final GeminiService geminiService;

    public SessionReportView getReport(String userId, String sessionId) {
        sessionService.requireSession(userId, sessionId);
        Optional<SessionReportDocument> docOpt = reportRepository.findBySessionIdAndUserId(sessionId, userId);
        if (docOpt.isEmpty()) {
            return new SessionReportView("pending", null, false, null, null);
        }
        SessionReportDocument doc = docOpt.get();
        return new SessionReportView(
                doc.getStatus() != null ? doc.getStatus() : "done",
                doc.getGeneratedAt(),
                Boolean.TRUE.equals(doc.getStale()),
                doc.getError(),
                doc.getReport()
        );
    }

    public SessionReportView triggerReport(String userId, String sessionId) {
        SessionDocument session = sessionService.requireSession(userId, sessionId);

        List<QuestionDocument> questions = questionRepository.findAllBySessionIdOrderBySeqAsc(sessionId);
        List<AnswerDocument> answers = answerRepository.findAllBySessionIdOrderBySeqAsc(sessionId);

        if (answers.isEmpty()) {
            SessionReportData emptyData = new SessionReportData();
            emptyData.setOverallScore(null);
            emptyData.setReadiness("insufficient_data");
            emptyData.setSummary("Chưa có đủ dữ liệu câu trả lời để tạo báo cáo đánh giá.");
            emptyData.setStrengths(List.of());
            emptyData.setImprovements(List.of());

            SessionReportDocument doc = saveReportDoc(userId, sessionId, emptyData);
            return new SessionReportView("done", doc.getGeneratedAt(), false, null, emptyData);
        }

        // Build transcript string for AI evaluation
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < questions.size(); i++) {
            QuestionDocument q = questions.get(i);
            sb.append(String.format("Câu hỏi %d (%s): %s\n", q.getIndex(), q.getCompetency() != null ? q.getCompetency().getName() : "", q.getText()));
            // Match answer
            Optional<AnswerDocument> ans = answers.stream().filter(a -> q.getId().equals(a.getQuestionId())).findFirst();
            if (ans.isPresent()) {
                String ansText = ans.get().getTextEdited() != null ? ans.get().getTextEdited() : ans.get().getTextOriginal();
                sb.append(String.format("Ứng viên trả lời: %s\n\n", ansText));
            } else {
                sb.append("Ứng viên trả lời: [Bỏ qua câu hỏi]\n\n");
            }
        }

        BlueprintView bp = sessionService.getBlueprint(userId, sessionId);
        List<BlueprintCompetency> comps = bp.getCompetencies();

        String roleName = session.getRole() != null ? session.getRole().getName() : "Software Engineer";
        SessionReportData reportData = geminiService.generateReport(roleName, session.getLevel(), sb.toString(), comps);

        SessionReportDocument doc = saveReportDoc(userId, sessionId, reportData);
        return new SessionReportView("done", doc.getGeneratedAt(), false, null, reportData);
    }

    private SessionReportDocument saveReportDoc(String userId, String sessionId, SessionReportData data) {
        SessionReportDocument doc = reportRepository.findBySessionIdAndUserId(sessionId, userId)
                .orElse(SessionReportDocument.builder()
                        .id(UUID.randomUUID().toString())
                        .sessionId(sessionId)
                        .userId(userId)
                        .attemptId(UUID.randomUUID().toString())
                        .createdAt(Instant.now())
                        .schemaVersion(1)
                        .build());

        doc.setStatus("done");
        doc.setStale(false);
        doc.setReport(data);
        doc.setGeneratedAt(Instant.now());
        doc.setUpdatedAt(Instant.now());
        return reportRepository.save(doc);
    }

    public void markStaleForSession(String userId, String sessionId) {
        Optional<SessionReportDocument> docOpt = reportRepository.findBySessionIdAndUserId(sessionId, userId);
        if (docOpt.isPresent()) {
            SessionReportDocument doc = docOpt.get();
            doc.setStale(true);
            doc.setUpdatedAt(Instant.now());
            reportRepository.save(doc);
        }
    }

    public SessionStats computeUserStats(String userId) {
        long totalSessions = sessionRepository.countByUserId(userId);
        long completedSessions = sessionRepository.countByUserIdAndStatus(userId, "completed");
        long totalAnswers = answerRepository.countByUserId(userId);

        List<SessionReportDocument> reports = reportRepository.findAllByUserIdOrderByCreatedAtDesc(userId);
        Double avgScore = null;
        List<RecentReportSummary> recentList = new ArrayList<>();
        List<WeakCompetencySummary> weakList = new ArrayList<>();

        double sumScores = 0;
        int countScores = 0;

        for (SessionReportDocument r : reports) {
            if (r.getReport() != null && r.getReport().getOverallScore() != null) {
                sumScores += r.getReport().getOverallScore();
                countScores++;
            }
            if (r.getReport() != null && recentList.size() < 5) {
                recentList.add(new RecentReportSummary(
                        r.getSessionId(),
                        "Phiên phỏng vấn",
                        r.getReport().getOverallScore(),
                        r.getReport().getReadiness(),
                        r.getGeneratedAt() != null ? r.getGeneratedAt() : r.getCreatedAt()
                ));
            }
        }

        if (countScores > 0) {
            avgScore = Math.round((sumScores / countScores) * 10.0) / 10.0;
        }

        return new SessionStats(
                (int) totalSessions,
                (int) completedSessions,
                (int) totalAnswers,
                avgScore,
                recentList,
                weakList
        );
    }

    public void updateAnswer(String userId, String answerId, String textEdited) {
        if (textEdited == null || textEdited.isBlank()) {
            throw new ApiException("validation_error", HttpStatus.UNPROCESSABLE_ENTITY, "Nội dung câu trả lời không được rỗng.");
        }
        AnswerDocument answer = answerRepository.findByIdAndUserId(answerId, userId)
                .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND, "Không tìm thấy câu trả lời."));

        answer.setTextEdited(textEdited);
        answerRepository.save(answer);

        markStaleForSession(userId, answer.getSessionId());
    }
}
