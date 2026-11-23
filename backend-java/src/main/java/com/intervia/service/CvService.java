package com.intervia.service;

import com.intervia.config.ApiException;
import com.intervia.dto.CvDto.*;
import com.intervia.model.CvDocument;
import com.intervia.model.CvVersionDocument;
import com.intervia.repository.CvRepository;
import com.intervia.repository.CvVersionRepository;
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
public class CvService {

    private final CvRepository cvRepository;
    private final CvVersionRepository cvVersionRepository;
    private final SessionRepository sessionRepository;
    private final FileParserService fileParserService;
    private final GeminiService geminiService;

    public List<CvSummary> listCvs(String userId) {
        List<CvDocument> docs = cvRepository.findAllByUserId(userId);
        List<CvSummary> result = new ArrayList<>();
        for (CvDocument doc : docs) {
            result.add(toSummary(doc, userId));
        }
        return result;
    }

    public CvDocument requireCv(String userId, String cvId) {
        return cvRepository.findByIdAndUserId(cvId, userId)
                .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND, "Không tìm thấy CV."));
    }

    public CvSummary getCv(String userId, String cvId) {
        return toSummary(requireCv(userId, cvId), userId);
    }

    public CvSummary uploadCv(String userId, String name, String mode, MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException("validation_error", HttpStatus.UNPROCESSABLE_ENTITY, "Chưa chọn file CV.", Map.of("file", "Chưa chọn file CV."));
        }
        String cleanName = (name != null && !name.isBlank()) ? name.trim() : file.getOriginalFilename();
        String fileText = fileParserService.extractText(file);
        if (fileText.isBlank()) {
            fileText = "CV Ứng viên: " + cleanName;
        }

        String cvId = UUID.randomUUID().toString();
        Instant now = Instant.now();

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("status", "running");
        analysis.put("mode", mode != null ? mode : "standard");

        CvDocument cv = CvDocument.builder()
                .id(cvId)
                .userId(userId)
                .name(cleanName)
                .fileName(file.getOriginalFilename())
                .fileId(UUID.randomUUID().toString())
                .contentType(file.getContentType() != null ? file.getContentType() : "application/octet-stream")
                .size(file.getSize())
                .analysis(analysis)
                .latestVersion(null)
                .deleting(false)
                .createdAt(now)
                .updatedAt(now)
                .schemaVersion(1)
                .build();
        cvRepository.save(cv);

        // Run analysis in background or synchronously
        runCvAnalysis(cvId, userId, fileText, 1, mode);

        return toSummary(requireCv(userId, cvId), userId);
    }

    @Async
    public void runCvAnalysis(String cvId, String userId, String cvText, int versionNumber, String mode) {
        try {
            CandidateProfile profile = geminiService.extractCvProfile(cvText);

            CvVersionDocument versionDoc = CvVersionDocument.builder()
                    .id(UUID.randomUUID().toString())
                    .cvId(cvId)
                    .userId(userId)
                    .version(versionNumber)
                    .origin(versionNumber == 1 ? "upload" : "reanalysis")
                    .mode(mode != null ? mode : "standard")
                    .profile(profile)
                    .createdAt(Instant.now())
                    .schemaVersion(1)
                    .build();
            cvVersionRepository.save(versionDoc);

            CvDocument cv = requireCv(userId, cvId);
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("status", "done");
            analysis.put("mode", mode != null ? mode : "standard");

            cv.setAnalysis(analysis);
            cv.setLatestVersion(versionNumber);
            cv.setUpdatedAt(Instant.now());
            cvRepository.save(cv);
        } catch (Exception e) {
            log.error("Error analyzing CV {}: {}", cvId, e.getMessage());
            CvDocument cv = requireCv(userId, cvId);
            Map<String, Object> analysis = new HashMap<>();
            analysis.put("status", "failed");
            analysis.put("error", Map.of("code", "llm_error", "message", e.getMessage()));
            cv.setAnalysis(analysis);
            cvRepository.save(cv);
        }
    }

    public AnalysisProgress getCvAnalysis(String userId, String cvId) {
        CvDocument cv = requireCv(userId, cvId);
        String status = getStatus(cv.getAnalysis());

        List<AnalysisStep> steps = List.of(
                new AnalysisStep("upload", "Đã tải CV", "done"),
                new AnalysisStep("extract", "Đang đọc CV", status.equals("pending") ? "running" : status),
                new AnalysisStep("profile", "Trích xuất hồ sơ", status)
        );

        Object error = cv.getAnalysis() != null ? cv.getAnalysis().get("error") : null;
        return new AnalysisProgress(status, steps, error);
    }

    public AnalysisProgress reanalyzeCv(String userId, String cvId, String mode) {
        CvDocument cv = requireCv(userId, cvId);
        int nextVer = (cv.getLatestVersion() != null ? cv.getLatestVersion() : 0) + 1;

        Optional<CvVersionDocument> latestVer = cvVersionRepository.findFirstByCvIdOrderByVersionDesc(cvId);
        String cvText = cv.getName();
        if (latestVer.isPresent() && latestVer.get().getProfile() != null) {
            cvText = "CV: " + cv.getName();
        }

        Map<String, Object> analysis = new HashMap<>();
        analysis.put("status", "running");
        analysis.put("mode", mode != null ? mode : "standard");
        cv.setAnalysis(analysis);
        cvRepository.save(cv);

        runCvAnalysis(cvId, userId, cvText, nextVer, mode);

        return getCvAnalysis(userId, cvId);
    }

    public CvSummary renameCv(String userId, String cvId, String newName) {
        if (newName == null || newName.isBlank()) {
            throw new ApiException("validation_error", HttpStatus.UNPROCESSABLE_ENTITY, "Tên không được để trống.", Map.of("name", "Tên không được để trống."));
        }
        CvDocument cv = requireCv(userId, cvId);
        cv.setName(newName.trim());
        cv.setUpdatedAt(Instant.now());
        cvRepository.save(cv);
        return toSummary(cv, userId);
    }

    public List<VersionInfo> listCvVersions(String userId, String cvId) {
        requireCv(userId, cvId);
        List<CvVersionDocument> list = cvVersionRepository.findAllByCvIdAndUserIdOrderByVersionDesc(cvId, userId);
        List<VersionInfo> result = new ArrayList<>();
        for (CvVersionDocument doc : list) {
            result.add(new VersionInfo(
                    doc.getVersion(),
                    doc.getOrigin() != null ? doc.getOrigin() : "upload",
                    doc.getMode() != null ? doc.getMode() : "standard",
                    doc.getCreatedAt()
            ));
        }
        return result;
    }

    public CvVersionView getCvVersion(String userId, String cvId, String versionStr) {
        requireCv(userId, cvId);
        CvVersionDocument doc;
        if ("latest".equalsIgnoreCase(versionStr)) {
            doc = cvVersionRepository.findFirstByCvIdOrderByVersionDesc(cvId)
                    .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản hồ sơ."));
        } else {
            int ver = Integer.parseInt(versionStr);
            doc = cvVersionRepository.findByCvIdAndUserIdAndVersion(cvId, userId, ver)
                    .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND, "Không tìm thấy phiên bản hồ sơ."));
        }
        return new CvVersionView(
                doc.getCvId(),
                doc.getVersion(),
                doc.getCreatedAt(),
                doc.getOrigin(),
                doc.getMode(),
                doc.getProfile() != null ? doc.getProfile() : new CandidateProfile()
        );
    }

    public CvVersionView saveCvVersion(String userId, String cvId, int baseVersion, CandidateProfile profile) {
        CvDocument cv = requireCv(userId, cvId);
        int nextVer = (cv.getLatestVersion() != null ? cv.getLatestVersion() : 0) + 1;

        CvVersionDocument versionDoc = CvVersionDocument.builder()
                .id(UUID.randomUUID().toString())
                .cvId(cvId)
                .userId(userId)
                .version(nextVer)
                .origin("user_edit")
                .mode(cv.getAnalysis() != null ? (String) cv.getAnalysis().get("mode") : "standard")
                .profile(profile)
                .createdAt(Instant.now())
                .schemaVersion(1)
                .build();
        cvVersionRepository.save(versionDoc);

        cv.setLatestVersion(nextVer);
        cv.setUpdatedAt(Instant.now());
        cvRepository.save(cv);

        return new CvVersionView(
                cvId,
                nextVer,
                versionDoc.getCreatedAt(),
                versionDoc.getOrigin(),
                versionDoc.getMode(),
                profile
        );
    }

    public boolean deleteCv(String userId, String cvId) {
        CvDocument cv = requireCv(userId, cvId);
        cvRepository.delete(cv);
        return true;
    }

    private CvSummary toSummary(CvDocument doc, String userId) {
        int sessionCount = sessionRepository.findAllByUserIdAndCvId(userId, doc.getId()).size();
        String status = getStatus(doc.getAnalysis());
        return new CvSummary(
                doc.getId(),
                doc.getName(),
                doc.getFileName(),
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
