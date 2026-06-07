package com.gpr.payroll.repository;

import com.gpr.common.entity.UserPosition;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

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
