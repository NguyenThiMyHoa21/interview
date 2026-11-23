package com.intervia.controller;

import com.intervia.dto.AuthDto.*;
import com.intervia.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/auth/me")
    public AuthState me(HttpServletRequest request) {
        return authService.getAuthState(request);
    }

    @PostMapping("/auth/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserOut signup(@RequestBody Credentials creds, HttpServletResponse response) {
        return authService.signup(creds, response);
    }

    @PostMapping("/auth/login")
    public UserOut login(@RequestBody Credentials creds, HttpServletResponse response) {
        return authService.login(creds, response);
    }

    @PostMapping("/auth/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout(HttpServletRequest request, HttpServletResponse response) {
        authService.logout(request, response);
    }

    @PostMapping("/auth/forgot-password")
    public ForgotPasswordResponse forgotPassword(@RequestBody ForgotPasswordRequest req) {
        return authService.forgotPassword(req);
    }

    @PostMapping("/auth/reset-password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void resetPassword(@RequestBody ResetPasswordRequest req) {
        authService.resetPassword(req);
    }

    @GetMapping("/account/data")
    public AccountDataView accountData(HttpServletRequest request) {
        return authService.getAccountData(request);
    }

    @PostMapping("/account/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void changePassword(HttpServletRequest request, @RequestBody PasswordChangeRequest req) {
        authService.changePassword(request, req);
    }

    @DeleteMapping("/account")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(HttpServletRequest request, HttpServletResponse response, @RequestBody DeleteAccountRequest req) {
        authService.deleteAccount(request, response, req);
    }
}
