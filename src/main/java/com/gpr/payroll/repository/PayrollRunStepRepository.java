package com.gpr.payroll.repository;

import com.gpr.common.entity.PayrollRunStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunStepRepository extends JpaRepository<PayrollRunStep, Long> {
    List<PayrollRunStep> findByPayrollRunIdOrderByStepOrder(Long payrollRunId);
}
