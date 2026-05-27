package com.gpm.payroll.repository;

import com.gpm.payroll.entity.PayrollSetup;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PayrollSetupRepository extends JpaRepository<PayrollSetup, Long> {

    @Query("SELECT s FROM PayrollSetup s WHERE s.jobPosition.id = :posId AND s.active = true AND s.deletedAt IS NULL")
    List<PayrollSetup> findActiveByJobPositionId(@Param("posId") Long posId);
}