package com.intervia.service;

import com.intervia.config.AppConstants;
import com.intervia.dto.AuthDto.AuthState;
import com.intervia.dto.AuthDto.Credentials;
import com.intervia.dto.AuthDto.UserOut;
import com.intervia.dto.AuthDto.UserView;
import com.intervia.model.UserDocument;
import com.intervia.repository.UserRepository;
import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.bson.Document;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final MongoTemplate mongoTemplate;

    @Value("${intervia.auth.enabled:false}")
    private boolean authEnabled;

    public String resolveCurrentUserId(HttpServletRequest request) {
        if (request != null && request.getCookies() != null) {
            for (Cookie c : request.getCookies()) {
                if ("intervia_session".equals(c.getName()) && c.getValue() != null && !c.getValue().isBlank()) {
                    String token = c.getValue().trim();
                    Query q = new Query(Criteria.where("_id").is(token));
                    Document sess = mongoTemplate.findOne(q, Document.class, "auth_sessions");
                    if (sess != null && sess.getString("user_id") != null) {
                        return sess.getString("user_id");
                    }
                }
            }
        }
        // Fallback: If any user exists in database, use their id so their existing CVs/sessions appear
        List<UserDocument> allUsers = userRepository.findAll();
        if (!allUsers.isEmpty()) {
            return allUsers.get(0).getId();
        }
        return AppConstants.DEFAULT_USER_ID;
    }

    public UserView getCurrentUser(HttpServletRequest request) {
        String userId = resolveCurrentUserId(request);
        Optional<UserDocument> u = userRepository.findById(userId);
        if (u.isPresent()) {
            return new UserView(u.get().getId(), u.get().getEmail());
        }
        return new UserView(userId, AppConstants.DEFAULT_USER_EMAIL);
    }

    public AuthState getAuthState(HttpServletRequest request) {
        return new AuthState(authEnabled, getCurrentUser(request));
    }

    public UserOut signup(Credentials creds) {
        String email = creds.getEmail() != null ? creds.getEmail().trim().toLowerCase() : "";
        Optional<UserDocument> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return new UserOut(new UserView(existing.get().getId(), existing.get().getEmail()));
        }
        UserDocument doc = UserDocument.builder()
                .id(UUID.randomUUID().toString())
                .email(email)
                .passwordHash(creds.getPassword())
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .schemaVersion(1)
                .build();
        userRepository.save(doc);
        return new UserOut(new UserView(doc.getId(), doc.getEmail()));
    }

    public UserOut login(Credentials creds) {
        String email = creds.getEmail() != null ? creds.getEmail().trim().toLowerCase() : "";
        Optional<UserDocument> existing = userRepository.findByEmail(email);
        if (existing.isPresent()) {
            return new UserOut(new UserView(existing.get().getId(), existing.get().getEmail()));
        }
        return signup(creds);
    }
}
