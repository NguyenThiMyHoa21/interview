package com.intervia.controller;

import com.intervia.dto.AuthDto.AuthState;
import com.intervia.dto.AuthDto.Credentials;
import com.intervia.dto.AuthDto.UserOut;
import com.intervia.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @GetMapping("/me")
    public AuthState me(HttpServletRequest request) {
        return authService.getAuthState(request);
    }

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public UserOut signup(@RequestBody Credentials creds) {
        return authService.signup(creds);
    }

    @PostMapping("/login")
    public UserOut login(@RequestBody Credentials creds) {
        return authService.login(creds);
    }

    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void logout() {
    }
}
