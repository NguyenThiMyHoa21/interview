package com.intervia.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.intervia.dto.CvDto.CandidateProfile;
import com.intervia.dto.CvDto.Claim;
import com.intervia.dto.CvDto.Experience;
import com.intervia.dto.CvDto.Skill;
import com.intervia.dto.JdDto.JdRequirements;
import com.intervia.dto.JdDto.RequirementItem;
import com.intervia.dto.ReportDto.SessionReportData;
import com.intervia.dto.SessionDto.BlueprintCompetency;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;

@Slf4j
@Service
public class GeminiService {

    private final String apiKey;
    private final String model;
    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;

    public GeminiService(
            @Value("${intervia.gemini.api-key}") String apiKey,
            @Value("${intervia.gemini.model:gemini-2.5-flash}") String model,
            ObjectMapper objectMapper
    ) {
        this.apiKey = apiKey;
        this.model = model;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(30))
                .build();
    }

    public String generate(String systemInstruction, String userPrompt, boolean jsonMode) {
        String url = String.format("https://generativelanguage.googleapis.com/v1beta/models/%s:generateContent?key=%s",
                model, apiKey);

        Map<String, Object> body = new HashMap<>();

        List<Map<String, Object>> contents = new ArrayList<>();
        Map<String, Object> userPart = Map.of("text", userPrompt);
        contents.add(Map.of("role", "user", "parts", List.of(userPart)));
        body.put("contents", contents);

        if (systemInstruction != null && !systemInstruction.isBlank()) {
            body.put("systemInstruction", Map.of("parts", List.of(Map.of("text", systemInstruction))));
        }

        Map<String, Object> genConfig = new HashMap<>();
        genConfig.put("temperature", 0.4);
        genConfig.put("maxOutputTokens", 4096);
        if (jsonMode) {
            genConfig.put("responseMimeType", "application/json");
        }
        body.put("generationConfig", genConfig);

        for (int attempt = 1; attempt <= 3; attempt++) {
            try {
                String requestJson = objectMapper.writeValueAsString(body);
                HttpRequest request = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .header("Content-Type", "application/json")
                        .timeout(Duration.ofSeconds(60))
                        .POST(HttpRequest.BodyPublishers.ofString(requestJson))
                        .build();

                HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

                if (response.statusCode() == 200) {
                    JsonNode root = objectMapper.readTree(response.body());
                    JsonNode candidates = root.path("candidates");
                    if (candidates.isArray() && !candidates.isEmpty()) {
                        JsonNode parts = candidates.get(0).path("content").path("parts");
                        StringBuilder textBuilder = new StringBuilder();
                        for (JsonNode part : parts) {
                            if (!part.has("thought") || !part.path("thought").asBoolean(false)) {
                                textBuilder.append(part.path("text").asText(""));
                            }
                        }
                        String result = textBuilder.toString().trim();
                        if (!result.isEmpty()) {
                            return result;
                        }
                    }
                } else if (response.statusCode() == 429 || response.statusCode() >= 500) {
                    log.warn("Gemini API call failed with status {}, retrying attempt {}/3...", response.statusCode(), attempt);
                    Thread.sleep(1500L * attempt);
                    continue;
                } else {
                    log.error("Gemini API error status {}: {}", response.statusCode(), response.body());
                    break;
                }
            } catch (Exception e) {
                log.error("Error communicating with Gemini (attempt {}): {}", attempt, e.getMessage());
                try {
                    Thread.sleep(1000L * attempt);
                } catch (InterruptedException ignored) {}
            }
        }
        throw new RuntimeException("Không nhận được phản hồi hợp lệ từ Gemini API.");
    }

    public CandidateProfile extractCvProfile(String cvText) {
        String system = """
                Bạn là chuyên gia phân tích CV kỹ thuật.
                Nhiệm vụ: Đọc văn bản CV và trích xuất thông tin dưới dạng JSON chuẩn với 3 mảng:
                1. "skills": danh sách kỹ năng công nghệ (mỗi mục: id, name, years: số năm hoặc null, source_quote: câu trích dẫn)
                2. "experiences": danh sách kinh nghiệm làm việc hoặc dự án (mỗi mục: id, organization: tên công ty/trường học, role: vai trò/tên dự án, period: thời gian, technologies: mảng các công nghệ sử dụng, source_quote)
                3. "claims": các khẳng định về thành tích hoặc kết quả nổi bật (mỗi mục: id, statement: câu khẳng định, metric, subject, verifiable: boolean, source_quote, quote_status: "found")
                Định dạng trả về duy nhất là JSON hợp lệ, không giải thích thêm.
                """;
        String prompt = "Nội dung CV:\n" + cvText;
        try {
            String json = generate(system, prompt, true);
            JsonNode root = objectMapper.readTree(json);

            CandidateProfile profile = new CandidateProfile();
            if (root.has("skills")) {
                List<Skill> skills = objectMapper.convertValue(root.get("skills"), new TypeReference<List<Skill>>() {});
                int idx = 1;
                for (Skill s : skills) {
                    if (s.getId() == null || s.getId().isBlank()) s.setId("sk-" + (idx++));
                }
                profile.setSkills(skills);
            }
            if (root.has("experiences")) {
                List<Experience> exps = objectMapper.convertValue(root.get("experiences"), new TypeReference<List<Experience>>() {});
                int idx = 1;
                for (Experience e : exps) {
                    if (e.getId() == null || e.getId().isBlank()) e.setId("exp-" + (idx++));
                }
                profile.setExperiences(exps);
            }
            if (root.has("claims")) {
                List<Claim> claims = objectMapper.convertValue(root.get("claims"), new TypeReference<List<Claim>>() {});
                int idx = 1;
                for (Claim c : claims) {
                    if (c.getId() == null || c.getId().isBlank()) c.setId("cl-" + (idx++));
                    if (c.getQuoteStatus() == null) c.setQuoteStatus("found");
                }
                profile.setClaims(claims);
            }
            return profile;
        } catch (Exception e) {
            log.error("Failed to parse CV extraction response: {}", e.getMessage());
            // Fallback empty profile
            return new CandidateProfile();
        }
    }

    public JdRequirements extractJdRequirements(String jdText) {
        String system = """
                Bạn là chuyên gia phân tích mô tả công việc (Job Description - JD).
                Nhiệm vụ: Đọc văn bản JD và trích xuất danh sách các yêu cầu tuyển dụng dưới dạng JSON:
                {
                   "requirements": [
                      {
                        "id": "rq1",
                        "name": "Tóm tắt yêu cầu ngắn gọn",
                        "kind": "required" (nếu bắt buộc) hoặc "preferred" (nếu ưu tiên/nên có),
                        "importance": 2 (1: thấp, 2: vừa, 3: cao),
                        "source_quote": "câu trích nguyên văn từ JD"
                      }
                   ]
                }
                Chỉ trả về JSON hợp lệ.
                """;
        String prompt = "Nội dung JD:\n" + jdText;
        try {
            String json = generate(system, prompt, true);
            JsonNode root = objectMapper.readTree(json);
            if (root.has("requirements")) {
                List<RequirementItem> items = objectMapper.convertValue(root.get("requirements"), new TypeReference<List<RequirementItem>>() {});
                int idx = 1;
                for (RequirementItem item : items) {
                    if (item.getId() == null || item.getId().isBlank()) item.setId("rq" + (idx++));
                    if (item.getKind() == null) item.setKind("required");
                    if (item.getImportance() <= 0) item.setImportance(2);
                }
                return new JdRequirements(items);
            }
            return new JdRequirements(new ArrayList<>());
        } catch (Exception e) {
            log.error("Failed to parse JD extraction response: {}", e.getMessage());
            return new JdRequirements(new ArrayList<>());
        }
    }

    public List<BlueprintCompetency> generateBlueprint(String cvInfo, String jdInfo, String roleName, String level) {
        String system = """
                Bạn là Trưởng nhóm phỏng vấn kỹ thuật cấp cao.
                Nhiệm vụ: Dựa vào hồ sơ ứng viên (CV), yêu cầu công việc (JD), vị trí (role) và cấp bậc (level),
                hãy xây dựng Kế hoạch phỏng vấn (Interview Blueprint) gồm từ 3 đến 5 năng lực cốt lõi (competencies).
                Định dạng trả về:
                {
                   "competencies": [
                      {
                         "id": "comp-1",
                         "name": "Tên nhóm năng lực (ví dụ: Kiến thức cốt lõi Java & Spring Boot, Thiết kế CSDL, Tư duy giải quyết vấn đề...)",
                         "weight": 0.35,
                         "depth_band": ["L1", "L3"],
                         "questions_min": 2,
                         "questions_max": 3
                      }
                   ]
                }
                Tổng trọng số (weight) phải xấp xỉ 1.0. Trả về đúng JSON, không có văn bản giải thích.
                """;
        String prompt = String.format("Vị trí: %s (Cấp bậc: %s)\n\nThông tin CV:\n%s\n\nThông tin JD:\n%s",
                roleName, level, cvInfo, jdInfo);
        try {
            String json = generate(system, prompt, true);
            JsonNode root = objectMapper.readTree(json);
            if (root.has("competencies")) {
                List<BlueprintCompetency> list = objectMapper.convertValue(root.get("competencies"), new TypeReference<List<BlueprintCompetency>>() {});
                int idx = 1;
                for (BlueprintCompetency comp : list) {
                    if (comp.getId() == null || comp.getId().isBlank()) comp.setId("comp-" + (idx++));
                    if (comp.getDepthBand() == null || comp.getDepthBand().isEmpty()) comp.setDepthBand(List.of("L1", "L2"));
                    if (comp.getQuestionsMin() <= 0) comp.setQuestionsMin(1);
                    if (comp.getQuestionsMax() <= 0) comp.setQuestionsMax(2);
                }
                return list;
            }
        } catch (Exception e) {
            log.error("Failed to parse Blueprint generation response: {}", e.getMessage());
        }

        // Fallback default competencies
        return List.of(
                new BlueprintCompetency("comp-1", "Kiến thức công nghệ cốt lõi (" + roleName + ")", 0.4, List.of("L1", "L3"), 2, 3),
                new BlueprintCompetency("comp-2", "Kinh nghiệm dự án & Xử lý tình huống", 0.4, List.of("L2", "L3"), 2, 3),
                new BlueprintCompetency("comp-3", "Tư duy thiết kế & Giải quyết vấn đề", 0.2, List.of("L1", "L2"), 1, 2)
        );
    }

    public String generateNextQuestion(String role, String level, String competencyName, String interviewHistory) {
        String system = """
                Bạn là người phỏng vấn kỹ thuật tiếng Việt chuyên nghiệp, thân thiện và sắc bén.
                Hãy đặt ĐÚNG MỘT câu hỏi phỏng vấn ngắn gọn (1-3 câu), rõ ràng, tập trung vào chủ đề năng lực yêu cầu.
                Không lặp lại các câu hỏi đã có trong lịch sử trao đổi.
                Không đưa ra đáp án hay lời giải thích.
                """;
        String prompt = String.format("Vị trí: %s (%s)\nChủ đề cần hỏi: %s\n\nLịch sử phỏng vấn trước đó:\n%s\n\nHãy đặt câu hỏi tiếp theo:",
                role, level, competencyName, interviewHistory);
        try {
            return generate(system, prompt, false);
        } catch (Exception e) {
            log.warn("Gemini generation failed, using fallback question: {}", e.getMessage());
            return String.format("Bạn có thể chia sẻ về kinh nghiệm thực tế của mình đối với %s và một tình huống kỹ thuật cụ thể bạn từng giải quyết không?", competencyName);
        }
    }

    public String generateClarification(String questionText) {
        String system = """
                Bạn là người phỏng vấn kỹ thuật.
                Ứng viên chưa hiểu rõ câu hỏi. Hãy diễn giải lại câu hỏi ngắn gọn (1-2 câu), dễ hiểu hơn mà KHÔNG tiết lộ đáp án.
                """;
        try {
            return generate(system, "Câu hỏi hiện tại:\n" + questionText, false);
        } catch (Exception e) {
            return "Ý của câu hỏi là muốn làm rõ cách bạn tiếp cận và áp dụng kiến thức này trong các tình huống thực tế.";
        }
    }

    public String generateHint(String questionText, int hintLevel) {
        String system = """
                Bạn là người phỏng vấn kỹ thuật.
                Hãy đưa ra MỘT gợi ý ngắn (1-2 câu) giúp ứng viên định hướng suy nghĩ mà KHÔNG giải hộ hoàn toàn.
                """;
        String prompt = String.format("Câu hỏi: %s\nMức độ gợi ý: %d (Mức 1: hướng tư duy chung, Mức 2: cụ thể hơn về từ khóa/khái niệm)",
                questionText, hintLevel);
        try {
            return generate(system, prompt, false);
        } catch (Exception e) {
            return "Hãy bắt đầu từ nguyên lý cơ bản của công nghệ hoặc bài toán này rồi phát triển lên cách giải quyết.";
        }
    }

    public SessionReportData generateReport(String role, String level, String interviewTranscript, List<BlueprintCompetency> competencies) {
        String system = """
                Bạn là Chuyên gia Đánh giá Phỏng vấn kỹ thuật (Evaluator) cực kỳ NGHIÊM KHẮC, KHÁCH QUAN và TRUNG THỰC.
                TUYỆT ĐỐI KHÔNG nịnh nọt hay cho điểm khuyến khích vô lý.

                QUY TẮC CHẤM ĐIỂM BẮT BUỘC (thang 0 - 10):
                - 0.0 - 2.0 điểm (status: "weak"): Câu trả lời tào lao, lạc đề, gõ bừa/vô nghĩa (vd: "abc", "123", "hehe", "không biết"), quá ngắn không có kiến thức chuyên môn, hoặc bỏ qua.
                - 2.5 - 4.5 điểm (status: "weak"): Có nhắc vài từ khóa nhưng sai bản chất hoặc trả lời hời hợt, thiếu logic.
                - 5.0 - 6.9 điểm (status: "adequate"): Đúng ý cơ bản nhưng thiếu ví dụ thực tế, thiếu chiều sâu kỹ thuật.
                - 7.0 - 8.5 điểm (status: "strong"): Trả lời đúng trọng tâm, giải thích rõ cơ chế và có ví dụ thực tế.
                - 8.6 - 10.0 điểm (status: "strong"): Xuất sắc, phân tích sâu trade-off, kiến trúc, hiệu năng và edge-case.

                QUY TẮC TỔNG HỢP:
                - overall_score phải tính trung thực theo chất lượng thực tế của các câu đã trả lời VÀ có trừ điểm nếu ứng viên trả lời quá ít câu hoặc bỏ qua nhiều. Nếu trả lời tào lao/lạc đề thì overall_score PHẢI <= 2.5 và readiness = "needs_practice".
                - Nếu ứng viên không thể hiện được điểm mạnh thực sự nào, để strengths = [] (mảng rỗng), KHÔNG bịa ra điểm mạnh giả.
                - Mọi nhận xét (summary, feedback, gaps, sample_answer, roadmap) phải viết bằng Tiếng Việt rõ ràng, chỉ rõ lỗi sai trong câu trả lời của ứng viên.

                Trả về ĐÚNG JSON theo cấu trúc sau (không bọc markdown, không văn bản phụ):
                {
                   "overall_score": <số thực 0.0 - 10.0, làm tròn 1 chữ số thập phân>,
                   "readiness": "ready" (>= 7.5) | "almost_ready" (5.5 - 7.4) | "needs_practice" (< 5.5),
                   "summary": "Nhận xét tổng quan thẳng thắn, trung thực về màn phỏng vấn",
                   "strengths": ["Điểm mạnh thực sự nếu có, hoặc mảng rỗng []"],
                   "improvements": ["Điểm yếu cụ thể cần khắc phục"],
                   "competencies": [
                      {
                         "competency_id": "...",
                         "competency_name": "...",
                         "weight": 0.4,
                         "score": 0.0,
                         "status": "strong" | "adequate" | "weak" | "not_assessed",
                         "summary": "Nhận xét cụ thể cho nhóm năng lực này"
                      }
                   ],
                   "questions": [
                      {
                         "question_id": "...",
                         "question_index": 1,
                         "question_text": "...",
                         "answer_excerpt": "trích ngắn câu trả lời thực tế của ứng viên",
                         "status": "strong" | "adequate" | "weak" | "not_assessed",
                         "score": 0,
                         "strengths": [],
                         "gaps": ["Thiếu kiến thức..."],
                         "feedback": "Nhận xét thẳng thắn vì sao chấm điểm này",
                         "sample_answer": "Gợi ý câu trả lời mẫu chuẩn kỹ thuật"
                      }
                   ],
                   "roadmap": [
                      {
                         "priority": "high" | "medium" | "low",
                         "competency_name": "...",
                         "goal": "Mục tiêu cụ thể cần đạt",
                         "action": "Hành động thực hành hoặc tài liệu nên học",
                         "practice_question": "Câu hỏi tự luyện tập thêm"
                      }
                   ]
                }
                """;
        String prompt = String.format("Vị trí: %s (%s)\n\nBiên bản phỏng vấn:\n%s", role, level, interviewTranscript);
        try {
            String raw = generate(system, prompt, true);
            String cleaned = stripCodeFences(raw);
            return objectMapper.readValue(cleaned, SessionReportData.class);
        } catch (Exception e) {
            log.error("Failed to generate or parse session report: {}", e.getMessage());
            return buildHeuristicFallbackReport(interviewTranscript);
        }
    }

    private String stripCodeFences(String text) {
        if (text == null) return "";
        String t = text.trim();
        if (t.startsWith("```")) {
            int firstNewline = t.indexOf('\n');
            if (firstNewline > 0) t = t.substring(firstNewline + 1);
            if (t.endsWith("```")) t = t.substring(0, t.length() - 3);
        }
        return t.trim();
    }

    private SessionReportData buildHeuristicFallbackReport(String transcript) {
        SessionReportData fallback = new SessionReportData();
        // Inspect actual answer lengths so nonsense/very short answers never get 7.0
        int answeredCount = 0;
        int meaningfulChars = 0;
        if (transcript != null) {
            for (String line : transcript.split("\n")) {
                if (line.startsWith("Ứng viên trả lời:")) {
                    String ans = line.substring("Ứng viên trả lời:".length()).trim();
                    if (!ans.isEmpty() && !ans.contains("[Bỏ qua câu hỏi]")) {
                        answeredCount++;
                        meaningfulChars += ans.length();
                    }
                }
            }
        }
        double avgLen = answeredCount > 0 ? (double) meaningfulChars / answeredCount : 0;
        if (answeredCount == 0) {
            fallback.setOverallScore(null);
            fallback.setReadiness("insufficient_data");
            fallback.setSummary("Chưa có câu trả lời nào để đánh giá.");
            fallback.setStrengths(List.of());
            fallback.setImprovements(List.of("Hãy trả lời ít nhất một vài câu hỏi để hệ thống đánh giá năng lực."));
        } else if (avgLen < 35) {
            fallback.setOverallScore(1.5);
            fallback.setReadiness("needs_practice");
            fallback.setSummary("Các câu trả lời còn quá ngắn, thiếu nội dung chuyên môn hoặc chưa đi đúng trọng tâm câu hỏi.");
            fallback.setStrengths(List.of());
            fallback.setImprovements(List.of(
                    "Trả lời đầy đủ ý, trình bày rõ nguyên lý kỹ thuật thay vì trả lời quá ngắn gọn",
                    "Bổ sung ví dụ dự án thực tế để chứng minh năng lực"
            ));
        } else if (avgLen < 120) {
            fallback.setOverallScore(4.5);
            fallback.setReadiness("needs_practice");
            fallback.setSummary("Ứng viên có trả lời nhưng nội dung còn sơ sài và thiếu chiều sâu kỹ thuật (tạm tính do AI đang quá tải).");
            fallback.setStrengths(List.of("Có tham gia trả lời câu hỏi phỏng vấn"));
            fallback.setImprovements(List.of("Cần giải thích chi tiết hơn về cách triển khai và xử lý tình huống thực tế"));
        } else {
            fallback.setOverallScore(6.0);
            fallback.setReadiness("almost_ready");
            fallback.setSummary("Ứng viên đã trình bày câu trả lời tương đối đầy đủ (tạm tính do AI đang quá tải — hãy bấm 'Đánh giá lại' sau vài giây).");
            fallback.setStrengths(List.of("Trình bày câu trả lời có độ dài và thông tin tương đối rõ ràng"));
            fallback.setImprovements(List.of("Bấm 'Đánh giá lại' để AI phân tích chi tiết từng câu hỏi"));
        }
        return fallback;
    }
}
