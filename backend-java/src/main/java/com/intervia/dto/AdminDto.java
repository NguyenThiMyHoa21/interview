package com.intervia.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AdminDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminOverview {
        private long total_users;
        private long total_admins;
        private long total_sessions;
        private long completed_sessions;
        private long total_cvs;
        private long total_jds;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminUserRow {
        private String id;
        private String email;
        private String role;
        private long session_count;
        private long cv_count;
        private String created_at;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdateRoleRequest {
        private String role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AdminSessionRow {
        private String id;
        private String user_email;
        private String role_name;
        private String level;
        private String status;
        private int asked;
        private int planned;
        private String created_at;
    }
}
