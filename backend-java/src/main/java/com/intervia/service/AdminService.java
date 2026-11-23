package com.intervia.service;

import com.intervia.config.ApiException;
import com.intervia.dto.AdminDto.*;
import com.intervia.model.SessionDocument;
import com.intervia.model.UserDocument;
import com.intervia.repository.SessionRepository;
import com.intervia.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AdminService {

    private final UserRepository userRepository;
    private final SessionRepository sessionRepository;
    private final MongoTemplate mongoTemplate;
    private final AuthService authService;

    public AdminOverview getOverview() {
        List<UserDocument> users = userRepository.findAll();
        long totalUsers = users.size();
        long totalAdmins = users.stream().filter(u -> "ADMIN".equalsIgnoreCase(authService.resolveRole(u))).count();
        long totalSessions = mongoTemplate.count(new Query(), "sessions");
        long completedSessions = mongoTemplate.count(new Query(Criteria.where("status").is("completed")), "sessions");
        long totalCvs = mongoTemplate.count(new Query(), "cvs");
        long totalJds = mongoTemplate.count(new Query(), "jds");
        return new AdminOverview(totalUsers, totalAdmins, totalSessions, completedSessions, totalCvs, totalJds);
    }

    public List<AdminUserRow> listUsers() {
        List<UserDocument> users = userRepository.findAll(Sort.by(Sort.Direction.DESC, "created_at"));
        return users.stream().map(u -> {
            Query byUser = new Query(Criteria.where("user_id").is(u.getId()));
            long sessionCount = mongoTemplate.count(byUser, "sessions");
            long cvCount = mongoTemplate.count(byUser, "cvs");
            return new AdminUserRow(
                    u.getId(),
                    u.getEmail(),
                    authService.resolveRole(u),
                    sessionCount,
                    cvCount,
                    u.getCreatedAt() != null ? u.getCreatedAt().toString() : Instant.now().toString()
            );
        }).collect(Collectors.toList());
    }

    public AdminUserRow updateUserRole(String adminId, String targetUserId, String newRole) {
        String normalized = newRole != null ? newRole.trim().toUpperCase(Locale.ROOT) : "";
        if (!"USER".equals(normalized) && !"ADMIN".equals(normalized)) {
            throw new ApiException("validation_error", HttpStatus.UNPROCESSABLE_ENTITY, "Vai trò chỉ hỗ trợ USER hoặc ADMIN.");
        }
        if (adminId.equals(targetUserId) && !"ADMIN".equals(normalized)) {
            throw new ApiException("forbidden", HttpStatus.BAD_REQUEST, "Không thể tự hạ quyền ADMIN của chính mình.");
        }
        UserDocument target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND, "Không tìm thấy người dùng."));
        target.setRole(normalized);
        target.setUpdatedAt(Instant.now());
        userRepository.save(target);

        Query byUser = new Query(Criteria.where("user_id").is(target.getId()));
        long sessionCount = mongoTemplate.count(byUser, "sessions");
        long cvCount = mongoTemplate.count(byUser, "cvs");
        return new AdminUserRow(
                target.getId(),
                target.getEmail(),
                normalized,
                sessionCount,
                cvCount,
                target.getCreatedAt() != null ? target.getCreatedAt().toString() : Instant.now().toString()
        );
    }

    public void deleteUser(String adminId, String targetUserId) {
        if (adminId.equals(targetUserId)) {
            throw new ApiException("forbidden", HttpStatus.BAD_REQUEST, "Không thể tự xóa tài khoản đang đăng nhập từ trang quản trị.");
        }
        UserDocument target = userRepository.findById(targetUserId)
                .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND, "Không tìm thấy người dùng."));

        Query byUser = new Query(Criteria.where("user_id").is(targetUserId));
        mongoTemplate.remove(byUser, "sessions");
        mongoTemplate.remove(byUser, "blueprints");
        mongoTemplate.remove(byUser, "questions");
        mongoTemplate.remove(byUser, "answers");
        mongoTemplate.remove(byUser, "session_events");
        mongoTemplate.remove(byUser, "session_reports");
        mongoTemplate.remove(byUser, "cvs");
        mongoTemplate.remove(byUser, "cv_versions");
        mongoTemplate.remove(byUser, "jds");
        mongoTemplate.remove(byUser, "jd_versions");
        mongoTemplate.remove(byUser, "auth_sessions");
        mongoTemplate.remove(new Query(Criteria.where("email").is(target.getEmail())), "password_resets");
        userRepository.deleteById(targetUserId);
    }

    public List<AdminSessionRow> listAllSessions() {
        Map<String, String> emailById = userRepository.findAll().stream()
                .collect(Collectors.toMap(UserDocument::getId, UserDocument::getEmail, (a, b) -> a));

        List<SessionDocument> sessions = sessionRepository.findAll(Sort.by(Sort.Direction.DESC, "created_at"));
        return sessions.stream().map(s -> new AdminSessionRow(
                s.getId(),
                emailById.getOrDefault(s.getUserId(), s.getUserId()),
                s.getRole() != null && s.getRole().getName() != null ? s.getRole().getName() : "Software Engineer",
                s.getLevel() != null ? s.getLevel() : "junior",
                s.getStatus() != null ? s.getStatus() : "draft",
                s.getAskedCount() != null ? s.getAskedCount() : 0,
                s.getPlannedCount() != null ? s.getPlannedCount() : 0,
                s.getCreatedAt() != null ? s.getCreatedAt().toString() : Instant.now().toString()
        )).collect(Collectors.toList());
    }
}
