package com.gpm.payroll.repository;

import com.gpm.common.entity.PayrollRunStep;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface PayrollRunStepRepository extends JpaRepository<PayrollRunStep, Long> {
    List<PayrollRunStep> findByPayrollRunIdOrderByStepOrder(Long payrollRunId);
}
