package com.intervia.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

public class AuthDto {

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserView {
        private String id;
        private String email;
        private String role;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AuthState {
        private boolean auth_enabled;
        private UserView user;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Credentials {
        private String email;
        private String password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserOut {
        private UserView user;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class PasswordChangeRequest {
        private String current_password;
        private String new_password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForgotPasswordRequest {
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ForgotPasswordResponse {
        private String message;
        private String dev_code;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ResetPasswordRequest {
        private String email;
        private String code;
        private String new_password;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class DeleteAccountRequest {
        private String email;
    }

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AccountDataView {
        private long cv_count;
        private long jd_count;
        private long session_count;
        private long audio_count;
        private long audio_bytes;
    }
}
