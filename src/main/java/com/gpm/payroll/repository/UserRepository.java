package com.gpm.payroll.repository;

import com.gpm.common.entity.User;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Read-only access to the shared {@code users} table for payroll generation.
 * Writes go through wos-auth (which owns user lifecycle).
 */
public interface UserRepository extends JpaRepository<User, Long> {

    /**
     * All active users whose currently-active UserRole assignments include the given role name.
     * Used by payroll-run generation to enumerate teachers (or any other paid role).
     */
    @Query("""
            SELECT DISTINCT u FROM User u
            JOIN u.roleAssignments ra
            JOIN ra.userRole ur
            WHERE LOWER(ur.name) = LOWER(:roleName)
              AND u.active = true
              AND (ra.startAt IS NULL OR ra.startAt <= CURRENT_TIMESTAMP)
              AND (ra.endAt   IS NULL OR ra.endAt   >= CURRENT_TIMESTAMP)
            """)
    List<User> findActiveByRoleName(@Param("roleName") String roleName);

    List<User> findByActiveTrue();
}
