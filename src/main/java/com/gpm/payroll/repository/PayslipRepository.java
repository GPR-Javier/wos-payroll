package com.gpm.payroll.repository;

import com.gpm.common.entity.Payslip;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.math.BigDecimal;
import java.util.List;

public interface PayslipRepository extends JpaRepository<Payslip, Long> {
    @Query("SELECT p FROM Payslip p WHERE " +
           "(:runId IS NULL OR p.payrollRun.id = :runId) " +
           "AND (:status IS NULL OR p.status = :status) " +
           "AND (:search IS NULL OR LOWER(p.employeeName) LIKE LOWER(CONCAT('%',:search,'%')) " +
           "     OR LOWER(p.employeeId) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<Payslip> search(@Param("runId") Long runId,
                          @Param("status") String status,
                          @Param("search") String search,
                          Pageable pageable);

    List<Payslip> findByPayrollRunId(Long runId);

    @Query("SELECT COALESCE(SUM(p.incentives),0) FROM Payslip p WHERE p.payrollRun.status = 'released'")
    BigDecimal sumReleasedIncentives();
}
