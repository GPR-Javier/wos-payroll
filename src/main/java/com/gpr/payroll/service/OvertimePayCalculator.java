package com.gpr.payroll.service;

import com.gpr.common.entity.PayslipOvertimeLine;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Computes an employee's overtime + holiday/rest-day premium pay for a pay period from their APPROVED
 * overtime claims (owned by wos-hr, same {@code workos} DB) valued at the company's configured
 * multipliers ({@code overtime_rate_config}, falling back to the statutory PH defaults).
 *
 * <p>Reads via {@link JdbcTemplate} rather than mapping wos-hr's entities, so the two services stay
 * decoupled while sharing one database. Multi-tenancy is off in payroll (root resolver), so rows are
 * filtered explicitly by {@code user_id} / {@code company_id}.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class OvertimePayCalculator {

    /** Statutory PH defaults — must mirror wos-hr's OvertimeRateService.DEFAULTS (keyed by enum name). */
    private static final Map<String, Double> DEFAULTS = Map.ofEntries(
            Map.entry("REGULAR", 1.25),
            Map.entry("REST_DAY", 1.30),
            Map.entry("REST_DAY_OT", 1.69),
            Map.entry("REGULAR_HOLIDAY", 2.00),
            Map.entry("REGULAR_HOLIDAY_OT", 2.60),
            Map.entry("REGULAR_HOLIDAY_REST_DAY", 2.60),
            Map.entry("REGULAR_HOLIDAY_REST_DAY_OT", 3.38),
            Map.entry("SPECIAL_HOLIDAY", 1.30),
            Map.entry("SPECIAL_HOLIDAY_OT", 1.69),
            Map.entry("SPECIAL_HOLIDAY_REST_DAY", 1.50),
            Map.entry("SPECIAL_HOLIDAY_REST_DAY_OT", 1.95),
            Map.entry("EMERGENCY", 1.25));

    /** Display labels per type (mirror wos-ui OT_TYPE_LABEL) for payslip / PDF / Excel rendering. */
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("REGULAR", "Regular OT"),
            Map.entry("REST_DAY", "Rest Day"),
            Map.entry("REST_DAY_OT", "Rest Day OT"),
            Map.entry("REGULAR_HOLIDAY", "Regular Holiday"),
            Map.entry("REGULAR_HOLIDAY_OT", "Reg. Holiday OT"),
            Map.entry("REGULAR_HOLIDAY_REST_DAY", "Reg. Holiday + RD"),
            Map.entry("REGULAR_HOLIDAY_REST_DAY_OT", "Reg. Holiday + RD OT"),
            Map.entry("SPECIAL_HOLIDAY", "Special Holiday"),
            Map.entry("SPECIAL_HOLIDAY_OT", "Special Holiday OT"),
            Map.entry("SPECIAL_HOLIDAY_REST_DAY", "Special Holiday + RD"),
            Map.entry("SPECIAL_HOLIDAY_REST_DAY_OT", "Special Holiday + RD OT"),
            Map.entry("EMERGENCY", "Emergency OT"));

    // Standard PH working-hours basis for deriving an hourly rate from a monthly salary: 22 days × 8h.
    private static final BigDecimal HOURS_PER_MONTH = new BigDecimal("176");

    /** Human label for an overtime type name. */
    public static String label(String type) {
        return LABELS.getOrDefault(type, type);
    }

    /** Decimal hours as "H:MM" (e.g. 19.48 → "19:29"). */
    public static String hhmm(double hours) {
        int totalMin = (int) Math.round(hours * 60);
        return String.format("%d:%02d", totalMin / 60, totalMin % 60);
    }

    private final JdbcTemplate jdbc;

    /** Premium-pay result: the total plus a per-type breakdown (one line per overtime type). */
    public record Result(BigDecimal total, List<PayslipOvertimeLine> lines) {
        static Result empty() {
            return new Result(BigDecimal.ZERO, List.of());
        }
    }

    /**
     * Premium pay for the period, broken down per overtime type: each line is Σ hours and
     * Σ (hourly rate × hours × type multiplier) over the employee's approved claims of that type.
     * Best-effort — any read problem yields an empty result rather than failing payroll.
     */
    public Result compute(Long userId, Long companyId, BigDecimal monthlySalary,
                          LocalDate periodStart, LocalDate periodEnd) {
        if (userId == null || monthlySalary == null || monthlySalary.signum() <= 0
                || periodStart == null || periodEnd == null) {
            return Result.empty();
        }
        try {
            BigDecimal hourly = monthlySalary.divide(HOURS_PER_MONTH, 10, RoundingMode.HALF_UP);
            Map<String, Double> rates = loadRates(companyId);

            List<Map<String, Object>> claims = jdbc.queryForList(
                    "SELECT overtime_type, total_hours FROM overtime_requests "
                            + "WHERE user_id = ? AND status = 'APPROVED' AND overtime_date BETWEEN ? AND ?",
                    userId, periodStart, periodEnd);

            // Aggregate hours + amount per type (insertion order keeps the natural enum grouping).
            Map<String, double[]> byType = new LinkedHashMap<>(); // type -> [hours, amount]
            for (Map<String, Object> claim : claims) {
                String type = (String) claim.get("overtime_type");
                Object hoursObj = claim.get("total_hours");
                if (type == null || hoursObj == null) continue;
                double hours = ((Number) hoursObj).doubleValue();
                double mult = rates.getOrDefault(type, DEFAULTS.getOrDefault(type, 1.0));
                double amount = hourly.doubleValue() * hours * mult;
                double[] agg = byType.computeIfAbsent(type, k -> new double[2]);
                agg[0] += hours;
                agg[1] += amount;
            }

            List<PayslipOvertimeLine> lines = new ArrayList<>();
            BigDecimal total = BigDecimal.ZERO;
            for (Map.Entry<String, double[]> e : byType.entrySet()) {
                BigDecimal amount = BigDecimal.valueOf(e.getValue()[1]).setScale(2, RoundingMode.HALF_UP);
                double hours = Math.round(e.getValue()[0] * 100.0) / 100.0;
                lines.add(new PayslipOvertimeLine(e.getKey(), hours, amount));
                total = total.add(amount);
            }
            return new Result(total.setScale(2, RoundingMode.HALF_UP), lines);
        } catch (Exception e) {
            log.warn("Overtime pay computation failed for user {} ({}–{}): {}",
                    userId, periodStart, periodEnd, e.getMessage());
            return Result.empty();
        }
    }

    /** Company overrides merged over the statutory defaults; tolerates the config table not existing yet. */
    private Map<String, Double> loadRates(Long companyId) {
        Map<String, Double> rates = new HashMap<>(DEFAULTS);
        if (companyId == null) return rates;
        try {
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT overtime_type, multiplier FROM overtime_rate_config WHERE company_id = ?",
                    companyId);
            for (Map<String, Object> row : rows) {
                String type = (String) row.get("overtime_type");
                Object mult = row.get("multiplier");
                if (type != null && mult != null) rates.put(type, ((Number) mult).doubleValue());
            }
        } catch (Exception e) {
            log.debug("overtime_rate_config unavailable, using defaults for company {}: {}",
                    companyId, e.getMessage());
        }
        return rates;
    }
}
