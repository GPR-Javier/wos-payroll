package com.gpm.payroll.entity;

import com.gpm.common.entity.JobPosition;
import com.gpm.common.entity.SalaryGrade;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Read-only projection of the payroll_setups table for payroll computation.
 * Source of truth is owned and written by wos-hr.
 */
@Entity
@Table(name = "payroll_setups")
@Getter
@NoArgsConstructor
public class PayrollSetup {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_position_id")
    private JobPosition jobPosition;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "salary_grade_id")
    private SalaryGrade salaryGrade;

    @Column(name = "compensation_basis")
    private String compensationBasis;

    @Column(name = "cutoff_period")
    private String cutoffPeriod;

    @Column(name = "overtime_eligible")
    private boolean overtimeEligible;

    @Column(name = "overtime_rate", precision = 10, scale = 4)
    private BigDecimal overtimeRate;

    @Column(name = "allowances_json", columnDefinition = "text")
    private String allowancesJson;

    @Column(name = "deductions_json", columnDefinition = "text")
    private String deductionsJson;

    @Column(name = "effective_date")
    private LocalDate effectiveDate;

    @Column(name = "is_active")
    private boolean active;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}