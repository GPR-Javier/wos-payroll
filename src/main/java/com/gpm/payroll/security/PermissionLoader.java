package com.gpm.payroll.security;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Component;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionLoader {

    private final JdbcTemplate jdbc;

    private static final String SQL = """
            SELECT f.code
            FROM functionalities f
            JOIN user_role_access_roles urar ON f.access_role_id = urar.access_role_id
            WHERE urar.user_role_id = ? AND f.is_enabled = true
            """;

    public List<SimpleGrantedAuthority> loadByUserRoleId(Long userRoleId) {
        if (userRoleId == null) return List.of();
        try {
            return jdbc.queryForList(SQL, String.class, userRoleId)
                    .stream()
                    .filter(code -> code != null && !code.isBlank())
                    .map(SimpleGrantedAuthority::new)
                    .toList();
        } catch (Exception e) {
            log.warn("Failed to load permissions for userRoleId {}: {}", userRoleId, e.getMessage());
            return List.of();
        }
    }
}
