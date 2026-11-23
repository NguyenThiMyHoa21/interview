package com.intervia.controller;

import com.intervia.dto.AdminDto.*;
import com.intervia.model.UserDocument;
import com.intervia.service.AdminService;
import com.intervia.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AuthService authService;
    private final AdminService adminService;

    @GetMapping("/overview")
    public AdminOverview overview(HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminService.getOverview();
    }

    @GetMapping("/users")
    public List<AdminUserRow> users(HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminService.listUsers();
    }

    @PatchMapping("/users/{userId}/role")
    public AdminUserRow updateRole(
            HttpServletRequest request,
            @PathVariable String userId,
            @RequestBody UpdateRoleRequest req
    ) {
        UserDocument admin = authService.requireAdmin(request);
        return adminService.updateUserRole(admin.getId(), userId, req.getRole());
    }

    @DeleteMapping("/users/{userId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteUser(HttpServletRequest request, @PathVariable String userId) {
        UserDocument admin = authService.requireAdmin(request);
        adminService.deleteUser(admin.getId(), userId);
    }

    @GetMapping("/sessions")
    public List<AdminSessionRow> sessions(HttpServletRequest request) {
        authService.requireAdmin(request);
        return adminService.listAllSessions();
    }
}
