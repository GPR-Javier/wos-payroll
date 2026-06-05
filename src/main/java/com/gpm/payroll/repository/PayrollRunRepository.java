package com.gpm.payroll.repository;

import com.gpm.common.entity.PayrollRun;
import java.math.BigDecimal;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PayrollRunRepository extends JpaRepository<PayrollRun, Long> {
    Page<PayrollRun> findAllByOrderByCreatedAtDesc(Pageable pageable);

    @Query("SELECT COALESCE(SUM(r.totalAmount),0) FROM PayrollRun r WHERE r.status = 'released'")
    BigDecimal sumReleasedPayroll();

    @Query("SELECT COALESCE(SUM(r.totalAmount),0) FROM PayrollRun r WHERE r.status = 'draft' OR r.status = 'processing'")
    BigDecimal sumPendingProcessing();

    @Query("SELECT COUNT(r) FROM PayrollRun r WHERE r.status = 'draft' OR r.status = 'processing'")
    long countPending();
}
