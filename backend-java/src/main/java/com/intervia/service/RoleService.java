package com.intervia.service;

import com.intervia.dto.RoleDto;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class RoleService {

    private static final List<RoleDto> ROLES = List.of(
            new RoleDto("backend_engineer", "Backend Engineer"),
            new RoleDto("ai_engineer", "AI Engineer"),
            new RoleDto("frontend_engineer", "Frontend Engineer"),
            new RoleDto("data_engineer", "Data Engineer")
    );

    public List<RoleDto> listRoles() {
        return ROLES;
    }

    public RoleDto findRoleOrDefault(String roleId, String roleCustom) {
        if (roleCustom != null && !roleCustom.isBlank()) {
            return new RoleDto(roleId != null ? roleId : "custom", roleCustom.trim());
        }
        for (RoleDto r : ROLES) {
            if (r.getId().equalsIgnoreCase(roleId)) {
                return r;
            }
        }
        return ROLES.get(0);
    }
}
