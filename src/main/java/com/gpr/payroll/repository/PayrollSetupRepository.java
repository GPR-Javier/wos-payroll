package com.gpr.payroll.repository;

import com.gpr.payroll.entity.PayrollSetup;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PayrollSetupRepository extends JpaRepository<PayrollSetup, Long> {

    @Query("SELECT s FROM PayrollSetup s WHERE s.jobPosition.id = :posId AND s.active = true AND s.deletedAt IS NULL")
    List<PayrollSetup> findActiveByJobPositionId(@Param("posId") Long posId);
}
