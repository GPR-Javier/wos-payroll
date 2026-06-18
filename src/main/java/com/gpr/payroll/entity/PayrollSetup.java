package com.gpr.payroll.entity;

import com.gpr.common.entity.JobPosition;
import com.gpr.kernel.entity.Auditable;
import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * Read-only projection of the payroll_setups table for payroll computation.
 * Source of truth is owned and written by wos-hr. Lifecycle/audit columns
 * (is_active, deleted_at, ...) come from {@link Auditable}.
 */
@Entity
@Table(name = "payroll_setups")
@Getter
@NoArgsConstructor
public class PayrollSetup extends Auditable {

    @Id
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "job_position_id")
    private JobPosition jobPosition;

    /** Base pay for this setup — the amount payroll computes from (written by wos-hr). */
    @Column(name = "base_salary", precision = 12, scale = 2)
    private BigDecimal baseSalary;

    @Column(name = "currency", length = 10)
    private String currency;

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
}
