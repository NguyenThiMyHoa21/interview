package com.intervia.service;

import com.intervia.config.ApiException;
import com.intervia.config.AppConstants;
import com.intervia.dto.AuthDto.*;
import com.intervia.model.UserDocument;
import com.intervia.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private static final String SESSION_COOKIE = "intervia_session";
    private static final int MIN_PASSWORD_LENGTH = 6;
    private static final int SESSION_TTL_DAYS = 7;
    private static final int RESET_OTP_TTL_MINUTES = 10;
    private static final SecureRandom SECURE_RANDOM = new SecureRandom();

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    @Value("${intervia.auth.enabled:true}")
    private boolean authEnabled;

    public String resolveCurrentUserId(HttpServletRequest request) {
        String sessionUserId = findUserIdFromCookie(request);
        if (sessionUserId != null) {
            return sessionUserId;
        }
        if (authEnabled) {
            throw new ApiException("unauthenticated", HttpStatus.UNAUTHORIZED, "Bạn cần đăng nhập để tiếp tục.");
        }
        // Fallback when auth is disabled
        List<UserDocument> allUsers = userRepository.findAll();
        if (!allUsers.isEmpty()) {
            return allUsers.get(0).getId();
        }
        return AppConstants.DEFAULT_USER_ID;
    }

    public AuthState getAuthState(HttpServletRequest request) {
        if (!authEnabled) {
            String userId = resolveCurrentUserId(request);
            Optional<UserDocument> u = userRepository.findById(userId);
            UserView view = u.map(doc -> new UserView(doc.getId(), doc.getEmail()))
                    .orElse(new UserView(userId, AppConstants.DEFAULT_USER_EMAIL));
            return new AuthState(false, view);
        }
        String userId = findUserIdFromCookie(request);
        if (userId == null) {
            return new AuthState(true, null);
        }
        return userRepository.findById(userId)
                .map(u -> new AuthState(true, new UserView(u.getId(), u.getEmail())))
                .orElse(new AuthState(true, null));
    }

    public UserOut signup(Credentials creds, HttpServletResponse response) {
        String email = normalizeEmail(creds != null ? creds.getEmail() : null);
        String password = creds != null && creds.getPassword() != null ? creds.getPassword() : "";

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (email.isEmpty() || !email.matches("^\\S+@\\S+\\.\\S+$")) {
            fieldErrors.put("email", "Email chưa đúng định dạng.");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            fieldErrors.put("password", "Mật khẩu cần ít nhất " + MIN_PASSWORD_LENGTH + " ký tự.");
        }
        if (!fieldErrors.isEmpty()) {
            throw new ApiException("validation_error", HttpStatus.UNPROCESSABLE_ENTITY, "Thông tin đăng ký chưa hợp lệ.", fieldErrors);
        }

        if (userRepository.findByEmail(email).isPresent()) {
            throw new ApiException("validation_error", HttpStatus.CONFLICT,
                    "Email này đã được đăng ký.", Map.of("email", "Email này đã được đăng ký."));
        }

        Instant now = Instant.now();
        UserDocument doc = UserDocument.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .passwordHash(hashPassword(password))
                .createdAt(now)
                .updatedAt(now)
                .schemaVersion(1)
                .build();
        userRepository.save(doc);

        createSessionAndSetCookie(doc.getId(), response);
        return new UserOut(new UserView(doc.getId(), doc.getEmail()));
    }

    public UserOut login(Credentials creds, HttpServletResponse response) {
        String email = normalizeEmail(creds != null ? creds.getEmail() : null);
        String password = creds != null && creds.getPassword() != null ? creds.getPassword() : "";

        if (email.isEmpty() || password.isEmpty()) {
            throw new ApiException("invalid_credentials", HttpStatus.UNAUTHORIZED, "Vui lòng nhập đầy đủ email và mật khẩu.");
        }

        UserDocument user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("invalid_credentials", HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng."));

        if (!verifyPassword(password, user.getPasswordHash())) {
            throw new ApiException("invalid_credentials", HttpStatus.UNAUTHORIZED, "Email hoặc mật khẩu không đúng.");
        }

        createSessionAndSetCookie(user.getId(), response);
        return new UserOut(new UserView(user.getId(), user.getEmail()));
    }

    public void logout(HttpServletRequest request, HttpServletResponse response) {
        String token = extractCookie(request, SESSION_COOKIE);
        if (token != null) {
            mongoTemplate.remove(new Query(Criteria.where("_id").is(token)), "auth_sessions");
        }
        clearSessionCookie(response);
    }

    public ForgotPasswordResponse forgotPassword(ForgotPasswordRequest req) {
        String email = normalizeEmail(req != null ? req.getEmail() : null);
        if (email.isEmpty() || !email.matches("^\\S+@\\S+\\.\\S+$")) {
            throw new ApiException("validation_error", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Email chưa đúng định dạng.", Map.of("email", "Email chưa đúng định dạng."));
        }

        UserDocument user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND,
                        "Không tìm thấy tài khoản nào với email này."));

        String otp = String.format("%06d", SECURE_RANDOM.nextInt(1_000_000));
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofMinutes(RESET_OTP_TTL_MINUTES));

        // Remove previous reset codes for this email
        mongoTemplate.remove(new Query(Criteria.where("email").is(email)), "password_resets");

        Document resetDoc = new Document()
                .append("_id", UUID.randomUUID().toString())
                .append("user_id", user.getId())
                .append("email", email)
                .append("code", otp)
                .append("created_at", Date.from(now))
                .append("expires_at", Date.from(expiresAt));
        mongoTemplate.insert(resetDoc, "password_resets");

        log.info("[PASSWORD RESET] Email={} -> OTP={} (valid {} min)", email, otp, RESET_OTP_TTL_MINUTES);
        return new ForgotPasswordResponse(
                "Đã tạo mã xác nhận 6 số (có hiệu lực trong " + RESET_OTP_TTL_MINUTES + " phút).",
                otp
        );
    }

    public void resetPassword(ResetPasswordRequest req) {
        String email = normalizeEmail(req != null ? req.getEmail() : null);
        String code = req != null && req.getCode() != null ? req.getCode().trim() : "";
        String newPassword = req != null && req.getNew_password() != null ? req.getNew_password() : "";

        Map<String, String> fieldErrors = new LinkedHashMap<>();
        if (email.isEmpty()) fieldErrors.put("email", "Nhập email.");
        if (code.length() != 6) fieldErrors.put("code", "Mã xác nhận gồm 6 chữ số.");
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            fieldErrors.put("new_password", "Mật khẩu mới cần ít nhất " + MIN_PASSWORD_LENGTH + " ký tự.");
        }
        if (!fieldErrors.isEmpty()) {
            throw new ApiException("validation_error", HttpStatus.UNPROCESSABLE_ENTITY, "Thông tin đặt lại mật khẩu chưa hợp lệ.", fieldErrors);
        }

        Document resetDoc = mongoTemplate.findOne(
                new Query(Criteria.where("email").is(email).and("code").is(code)),
                Document.class,
                "password_resets"
        );
        if (resetDoc == null) {
            throw new ApiException("invalid_code", HttpStatus.BAD_REQUEST, "Mã xác nhận không đúng hoặc đã hết hạn.");
        }
        Date expiresAt = resetDoc.getDate("expires_at");
        if (expiresAt != null && expiresAt.toInstant().isBefore(Instant.now())) {
            mongoTemplate.remove(new Query(Criteria.where("_id").is(resetDoc.getString("_id"))), "password_resets");
            throw new ApiException("invalid_code", HttpStatus.BAD_REQUEST, "Mã xác nhận đã hết hạn, vui lòng yêu cầu mã mới.");
        }

        UserDocument user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ApiException("not_found", HttpStatus.NOT_FOUND, "Tài khoản không tồn tại."));

        user.setPasswordHash(hashPassword(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);

        // Clean up OTP & revoke old sessions
        mongoTemplate.remove(new Query(Criteria.where("email").is(email)), "password_resets");
        mongoTemplate.remove(new Query(Criteria.where("user_id").is(user.getId())), "auth_sessions");
    }

    public void changePassword(HttpServletRequest request, PasswordChangeRequest req) {
        String userId = resolveCurrentUserId(request);
        UserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("unauthenticated", HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ."));

        String currentPassword = req != null && req.getCurrent_password() != null ? req.getCurrent_password() : "";
        String newPassword = req != null && req.getNew_password() != null ? req.getNew_password() : "";

        if (!verifyPassword(currentPassword, user.getPasswordHash())) {
            throw new ApiException("validation_error", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Mật khẩu hiện tại không đúng.", Map.of("current_password", "Mật khẩu hiện tại không đúng."));
        }
        if (newPassword.length() < MIN_PASSWORD_LENGTH) {
            throw new ApiException("validation_error", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Mật khẩu mới cần ít nhất " + MIN_PASSWORD_LENGTH + " ký tự.",
                    Map.of("new_password", "Mật khẩu mới cần ít nhất " + MIN_PASSWORD_LENGTH + " ký tự."));
        }

        user.setPasswordHash(hashPassword(newPassword));
        user.setUpdatedAt(Instant.now());
        userRepository.save(user);
    }

    public AccountDataView getAccountData(HttpServletRequest request) {
        String userId = resolveCurrentUserId(request);
        Query byUser = new Query(Criteria.where("user_id").is(userId));
        long cvCount = mongoTemplate.count(byUser, "cvs");
        long jdCount = mongoTemplate.count(byUser, "jds");
        long sessionCount = mongoTemplate.count(byUser, "sessions");
        return new AccountDataView(cvCount, jdCount, sessionCount, 0L, 0L);
    }

    public void deleteAccount(HttpServletRequest request, HttpServletResponse response, DeleteAccountRequest req) {
        String userId = resolveCurrentUserId(request);
        UserDocument user = userRepository.findById(userId)
                .orElseThrow(() -> new ApiException("unauthenticated", HttpStatus.UNAUTHORIZED, "Phiên đăng nhập không hợp lệ."));

        String confirmEmail = normalizeEmail(req != null ? req.getEmail() : null);
        if (!user.getEmail().equalsIgnoreCase(confirmEmail)) {
            throw new ApiException("validation_error", HttpStatus.UNPROCESSABLE_ENTITY,
                    "Email xác nhận không khớp.", Map.of("email", "Email xác nhận không khớp."));
        }

        Query byUser = new Query(Criteria.where("user_id").is(userId));
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
        mongoTemplate.remove(new Query(Criteria.where("email").is(user.getEmail())), "password_resets");
        userRepository.deleteById(userId);

        clearSessionCookie(response);
    }

    // ---- Helpers ----

    private String findUserIdFromCookie(HttpServletRequest request) {
        String token = extractCookie(request, SESSION_COOKIE);
        if (token == null) return null;
        Document sess = mongoTemplate.findOne(new Query(Criteria.where("_id").is(token)), Document.class, "auth_sessions");
        if (sess == null) return null;
        Date expiresAt = sess.getDate("expires_at");
        if (expiresAt != null && expiresAt.toInstant().isBefore(Instant.now())) {
            mongoTemplate.remove(new Query(Criteria.where("_id").is(token)), "auth_sessions");
            return null;
        }
        return sess.getString("user_id");
    }

    private String extractCookie(HttpServletRequest request, String name) {
        if (request == null || request.getCookies() == null) return null;
        for (Cookie c : request.getCookies()) {
            if (name.equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                return c.getValue().trim();
            }
        }
        return null;
    }

    private void createSessionAndSetCookie(String userId, HttpServletResponse response) {
        String token = UUID.randomUUID().toString().replace("-", "") + UUID.randomUUID().toString().replace("-", "");
        Instant now = Instant.now();
        Instant expiresAt = now.plus(Duration.ofDays(SESSION_TTL_DAYS));

        Document sessDoc = new Document()
                .append("_id", token)
                .append("user_id", userId)
                .append("created_at", Date.from(now))
                .append("expires_at", Date.from(expiresAt));
        mongoTemplate.insert(sessDoc, "auth_sessions");

        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, token)
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(Duration.ofDays(SESSION_TTL_DAYS))
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private void clearSessionCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from(SESSION_COOKIE, "")
                .httpOnly(true)
                .path("/")
                .sameSite("Lax")
                .maxAge(0)
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
    }

    private String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private String hashPassword(String password) {
        try {
            byte[] salt = new byte[16];
            SECURE_RANDOM.nextBytes(salt);
            PBEKeySpec spec = new PBEKeySpec(password.toCharArray(), salt, 65536, 256);
            SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
            byte[] hash = skf.generateSecret(spec).getEncoded();
            return "pbkdf2$" + Base64.getEncoder().encodeToString(salt) + "$" + Base64.getEncoder().encodeToString(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Không thể mã hóa mật khẩu", e);
        }
    }

    private boolean verifyPassword(String rawPassword, String storedHash) {
        if (rawPassword == null || storedHash == null || storedHash.isBlank()) {
            return false;
        }
        if (storedHash.startsWith("pbkdf2$")) {
            String[] parts = storedHash.split("\\$");
            if (parts.length != 3) return false;
            try {
                byte[] salt = Base64.getDecoder().decode(parts[1]);
                byte[] expected = Base64.getDecoder().decode(parts[2]);
                PBEKeySpec spec = new PBEKeySpec(rawPassword.toCharArray(), salt, 65536, 256);
                SecretKeyFactory skf = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256");
                byte[] actual = skf.generateSecret(spec).getEncoded();
                return MessageDigest.isEqual(expected, actual);
            } catch (Exception e) {
                return false;
            }
        }
        // Fallback for legacy plain-text entries if any
        return storedHash.equals(rawPassword);
    }
}
