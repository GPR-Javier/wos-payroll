package com.gpm.payroll.repository;

import com.gpm.common.entity.PayrollRunStep;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PayrollRunStepRepository extends JpaRepository<PayrollRunStep, Long> {
    List<PayrollRunStep> findByPayrollRunIdOrderByStepOrder(Long payrollRunId);
}
