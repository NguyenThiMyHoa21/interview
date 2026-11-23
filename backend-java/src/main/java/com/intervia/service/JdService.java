package com.intervia.service;

import com.intervia.config.ApiException;
import com.intervia.dto.CvDto.AnalysisProgress;
import com.intervia.dto.CvDto.AnalysisStep;
import com.intervia.dto.CvDto.VersionInfo;
import com.intervia.dto.JdDto.*;
import com.intervia.model.JdDocument;
import com.intervia.model.JdVersionDocument;
import com.intervia.repository.JdRepository;
import com.intervia.repository.JdVersionRepository;
import com.intervia.repository.SessionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class JdService {

    private final JdRepository jdRepository;
    private final JdVersionRepository jdVersionRepository;
    private final SessionRepository sessionRepository;
    private final FileParserService fileParserService;
    private final GeminiService geminiService;

    public List<JdSummary> listJds(String userId) {
        List<JdDocument> docs = jdRepository.findAllByUserId(userId);
        List<JdSummary> result = new ArrayList<>();
        for (JdDocument doc : docs) {
            result.add(toSummary(doc, userId));
        }
        return result;
    }

    public JdDocument requireJd(String userId, String jdId) {
        return jdRepository.findByIdAndUserId(jdId, userId)
                .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND, "Không tìm thấy JD."));
    }

    public JdSummary getJd(String userId, String jdId) {
        return toSummary(requireJd(userId, jdId), userId);
    }

    public JdSummary createJd(String userId, String name, String text, String mode, MultipartFile file) {
        String jdText = text;
        String cleanName = name;
        if (file != null && !file.isEmpty()) {
            jdText = fileParserService.extractText(file);
            if (cleanName == null || cleanName.isBlank()) {
                cleanName = file.getOriginalFilename();
            }
        }
        if (cleanName == null || cleanName.isBlank()) {
            cleanName = "Mô tả công việc";
        }
        if (jdText == null || jdText.isBlank()) {
            throw new ApiException("validation_error", HttpStatus.UNPROCESSABLE_ENTITY, "Nội dung JD không được để trống.", Map.of("text", "Chưa nhập nội dung JD."));
        }

        String jdId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("status", "running");
        analysis.put("mode", mode != null ? mode : "standard");

        JdDocument jd = JdDocument.builder()
                .id(jdId)
                .userId(userId)
                .name(cleanName)
                .text(jdText)
                .company(null)
                .position(null)
                .requirementCount(0)
                .analysis(analysis)
                .latestVersion(null)
                .deleting(false)
                .createdAt(now)
                .updatedAt(now)
                .schemaVersion(1)
                .build();
        jdRepository.save(jd);

        runJdAnalysis(jdId, userId, jdText, 1, mode);

        return toSummary(requireJd(userId, jdId), userId);
    }

    @Async
    public void runJdAnalysis(String jdId, String userId, String jdText, int versionNumber, String mode) {
        try {
            JdRequirements requirements = geminiService.extractJdRequirements(jdText);

            JdVersionDocument versionDoc = JdVersionDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .jdId(jdId)
                    .userId(userId)
                    .version(versionNumber)
                    .origin(versionNumber == 1 ? "create" : "reanalysis")
                    .mode(mode != null ? mode : "standard")
                    .requirements(requirements)
                    .createdAt(Instant.now())
                    .schemaVersion(1)
                    .build();
            jdVersionRepository.save(versionDoc);

            JdDocument jd = requireJd(userId, jdId);
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("status", "done");
            analysis.put("mode", mode != null ? mode : "standard");

            jd.setAnalysis(analysis);
            jd.setLatestVersion(versionNumber);
            jd.setRequirementCount(requirements.getRequirements() != null ? requirements.getRequirements().size() : 0);
            jd.setUpdatedAt(Instant.now());
            jdRepository.save(jd);
        } catch (Exception e) {
            log.error("Error analyzing JD {}: {}", jdId, e.getMessage());
            JdDocument jd = requireJd(userId, jdId);
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("status", "failed");
            analysis.put("error", Map.of("code", "llm_error", "message", e.getMessage()));
            jd.setAnalysis(analysis);
            jdRepository.save(jd);
        }
    }

    public AnalysisProgress getJdAnalysis(String userId, String jdId) {
        JdDocument jd = requireJd(userId, jdId);
        String status = getStatus(jd.getAnalysis());

        List<AnalysisStep> steps = List.of(
                new AnalysisStep("upload", "Đã nhận JD", "done"),
                new AnalysisStep("extract", "Đang đọc JD", status.equals("pending") ? "running" : status),
                new AnalysisStep("requirements", "Trích xuất yêu cầu", status)
        );

        Object error = jd.getAnalysis() != null ? jd.getAnalysis().get("error") : null;
        return new AnalysisProgress(status, steps, error);
    }

    public AnalysisProgress reanalyzeJd(String userId, String jdId, String mode) {
        JdDocument jd = requireJd(userId, jdId);
        int nextVer = (jd.getLatestVersion() != null ? jd.getLatestVersion() : 0) + 1;

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("status", "running");
        analysis.put("mode", mode != null ? mode : "standard");
        jd.setAnalysis(analysis);
        jdRepository.save(jd);

        runJdAnalysis(jdId, userId, jd.getText(), nextVer, mode);

        return getJdAnalysis(userId, jdId);
    }

    public JdSummary renameJd(String userId, String jdId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new ApiException("validation_error", HttpStatus.UNPROCESSABLE_ENTITY, "Tên không được để trống.", Map.of("name", "Tên không được để trống."));
        }
        JdDocument jd = requireJd(userId, jdId);
        jd.setName(newName.trim());
        jd.setUpdatedAt(Instant.now());
        jdRepository.save(jd);
        return toSummary(jd, userId);
    }

    public List<VersionInfo> listJdVersions(String userId, String jdId) {
        requireJd(userId, jdId);
        List<JdVersionDocument> list = jdVersionRepository.findAllByJdIdAndUserIdOrderByVersionDesc(jdId, userId);
        List<VersionInfo> result = new ArrayList<>();
        for (JdVersionDocument doc : list) {
            result.add(new VersionInfo(
                    doc.getVersion(),
                    doc.getOrigin() != null ? doc.getOrigin() : "create",
                    doc.getMode() != null ? doc.getMode() : "standard",
                    doc.getCreatedAt()
            ));
        }
        return result;
    }

    public JdVersionView getJdVersion(String userId, String jdId, String versionStr) {
        requireJd(userId, jdId);
        JdVersionDocument doc;
        if ("latest".equalsIgnoreCase(versionStr)) {
            doc = jdVersionRepository.findFirstByJdIdOrderByVersionDesc(jdId)
                    .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản JD."));
        } else {
            int ver = Integer.parseInt(versionStr);
            doc = jdVersionRepository.findByJdIdAndUserIdAndVersion(jdId, userId, ver)
                    .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản JD."));
        }
        return new JdVersionView(
                doc.getJdId(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getOrigin(),
                doc.getMode(),
                doc.getRequirements() != null ? doc.getRequirements() : new JdRequirements(new ArrayList<>())
        );
    }

    public JdVersionView saveJdVersion(String userId, String jdId, int baseVersion, JdRequirements requirements) {
        JdDocument jd = requireJd(userId, jdId);
        int nextVer = (jd.getLatestVersion() != null ? jd.getLatestVersion() : 0) + 1;

        JdVersionDocument versionDoc = JdVersionDocument.builder()
                .id(UUID.randomUUID().toString())
                .jdId(jdId)
                .userId(userId)
                .version(nextVer)
                .origin("user_edit")
                .mode(jd.getAnalysis() != null ? (String) jd.getAnalysis().get("mode") : "standard")
                .requirements(requirements)
                .createdAt(Instant.now())
                .schemaVersion(1)
                .build();
        jdVersionRepository.save(versionDoc);

        jd.setLatestVersion(nextVer);
        jd.setRequirementCount(requirements.getRequirements() != null ? requirements.getRequirements().size() : 0);
        jd.setUpdatedAt(Instant.now());
        jdRepository.save(jd);

        return new JdVersionView(
                jdId,
                nextVer,
                versionDoc.getCreatedAt(),
                versionDoc.getOrigin(),
                versionDoc.getMode(),
                requirements
        );
    }

    public boolean deleteJd(String userId, String jdId) {
        JdDocument jd = requireJd(userId, jdId);
        jdRepository.delete(jd);
        return true;
    }

    private JdSummary toSummary(JdDocument doc, String userId) {
        int sessionCount = sessionRepository.findAllByUserIdAndJdId(userId, doc.getId()).size();
        String status = getStatus(doc.getAnalysis());
        return new JdSummary(
                doc.getId(),
                doc.getName(),
                doc.getCompany(),
                doc.getPosition(),
                doc.getRequirementCount() != null ? doc.getRequirementCount() : 0,
                doc.getLatestVersion(),
                status,
                sessionCount,
                doc.getCreatedAt(),
                doc.getUpdatedAt()
        );
    }

    private String getStatus(Map<String, Object> analysis) {
        if (analysis == null) return "done";
        Object st = analysis.get("status");
        return st != null ? st.toString() : "done";
    }
}
