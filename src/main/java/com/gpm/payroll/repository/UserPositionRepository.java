package com.gpm.payroll.repository;

import com.gpm.common.entity.UserPosition;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserPositionRepository extends JpaRepository<UserPosition, Long> {

    @Query("""
            SELECT up FROM UserPosition up
            WHERE up.user.id = :userId
              AND up.primary = true
              AND up.removedAt IS NULL
              AND (up.endDate IS NULL OR up.endDate >= CURRENT_DATE)
            """)
    List<UserPosition> findPrimaryActiveByUserId(@Param("userId") Long userId);
}