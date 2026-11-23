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
}
