package com.gpr.payroll.service;

import com.gpr.common.entity.*;
import com.gpr.payroll.repository.*;
import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.*;
import lombok.RequiredArgsConstructor;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.ObjectMapper;

@Service
@RequiredArgsConstructor
public class PayrollService {

    private static final List<Map.Entry<String, String>> STEPS = List.of(
            Map.entry("ATTENDANCE_FINALIZED", "Attendance Finalized"),
            Map.entry("HOURS_COMPUTED", "Hours Computed"),
            Map.entry("INCENTIVES_ADDED", "Incentives Added"),
            Map.entry("DEDUCTIONS_APPLIED", "Deductions Applied"),
            Map.entry("PAYROLL_GENERATED", "Payroll Generated"),
            Map.entry("PAYSLIP_RELEASED", "Payslip Released")
    );

    // ── Philippine statutory rates ─────────────────────────────────────────────
    private static final BigDecimal SSS_RATE        = new BigDecimal("0.045");
    private static final BigDecimal SSS_MAX         = new BigDecimal("1350.00");
    private static final BigDecimal SSS_MIN         = new BigDecimal("180.00");
    private static final BigDecimal PHILHEALTH_RATE = new BigDecimal("0.025");
    private static final BigDecimal PHILHEALTH_MAX  = new BigDecimal("2500.00");
    private static final BigDecimal PHILHEALTH_MIN  = new BigDecimal("500.00");
    private static final BigDecimal PAGIBIG_RATE    = new BigDecimal("0.02");
    private static final BigDecimal PAGIBIG_MAX     = new BigDecimal("200.00");
    private static final BigDecimal TWO             = BigDecimal.valueOf(2);
    private static final BigDecimal FOUR_333        = new BigDecimal("4.333");
    private static final BigDecimal TWENTY_TWO      = BigDecimal.valueOf(22);

    private final PayrollRunRepository     runRepo;
    private final PayrollRunStepRepository stepRepo;
    private final PayslipRepository        payslipRepo;
    private final UserRepository           userRepo;
    private final UserPositionRepository   userPositionRepo;
    private final PayrollSetupRepository   payrollSetupRepo;
    private final ObjectMapper             objectMapper;
    private final com.gpr.payroll.client.UserDirectoryClient userDirectory;
    private final OvertimePayCalculator    overtimePayCalculator;

    // ── Stats ─────────────────────────────────────────────────────────────────

    public Map<String, Object> getStats() {
        BigDecimal released   = runRepo.sumReleasedPayroll();
        BigDecimal pending    = runRepo.sumPendingProcessing();
        BigDecimal incentives = payslipRepo.sumReleasedIncentives();
        long pendingCount     = runRepo.countPending();

        return Map.of(
                "totalPayrollAmount",       released.add(pending),
                "pendingProcessing",        pendingCount,
                "releasedPayroll",          released,
                "incentivesTotal",          incentives,
                "totalBasicSalary",         payslipRepo.sumReleasedBasicSalary(),
                "totalIncentives",          incentives,
                "totalAbsences",            payslipRepo.sumReleasedAbsences(),
                "totalLatePenalties",       payslipRepo.sumReleasedLatePenalties(),
                "totalCashAdvances",        payslipRepo.sumReleasedCashAdvances(),
                "totalStatutoryDeductions", payslipRepo.sumReleasedStatutoryDeductions()
        );
    }

    // ── Runs ──────────────────────────────────────────────────────────────────

    public Page<PayrollRun> getRuns(int page, int size) {
        return runRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    @Transactional
    public PayrollRun createRun(LocalDate periodStart, LocalDate periodEnd) {
        String creator = SecurityContextHolder.getContext().getAuthentication().getName();
        String period  = periodStart.format(DateTimeFormatter.ofPattern("MMMM yyyy"));

        PayrollRun run = PayrollRun.builder()
                .period(period)
                .periodStart(periodStart)
                .periodEnd(periodEnd)
                .status("draft")
                .createdBy(creator)
                .build();
        run = runRepo.save(run);

        List<PayrollRunStep> steps = new ArrayList<>();
        for (int i = 0; i < STEPS.size(); i++) {
            var entry = STEPS.get(i);
            steps.add(PayrollRunStep.builder()
                    .payrollRun(run)
                    .step(entry.getKey())
                    .label(entry.getValue())
                    .stepOrder(i + 1)
                    .status("pending")
                    .build());
        }
        stepRepo.saveAll(steps);
        return run;
    }

    public List<PayrollRunStep> getSteps(Long runId) {
        return stepRepo.findByPayrollRunIdOrderByStepOrder(runId);
    }

    @Transactional
    public PayrollRun processRun(Long runId) {
        PayrollRun run = runRepo.findById(runId).orElseThrow();
        run.setStatus("processing");
        run.setProcessedAt(LocalDateTime.now());

        List<User> employees = userRepo.findByActiveTrue();
        List<Payslip> payslips = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;

        // Resolve every employee's display name in one directory call instead of one HTTP round trip
        // per employee inside computePayslip.
        java.util.Map<Long, com.gpr.kernel.dto.UserSummaryDto> summaries = userDirectory.getSummaries(
                employees.stream().map(User::getUserId).toList());

        for (User emp : employees) {
            // Resolve primary active position
            List<UserPosition> positions = userPositionRepo.findPrimaryActiveByUserId(emp.getId());
            if (positions.isEmpty()) continue;
            JobPosition position = positions.get(0).getJobPosition();

            // Find the most recently effective active PayrollSetup for this position
            List<com.gpr.payroll.entity.PayrollSetup> setups =
                    payrollSetupRepo.findActiveByJobPositionId(position.getId());
            if (setups.isEmpty()) continue;

            com.gpr.payroll.entity.PayrollSetup setup = setups.stream()
                    .max(Comparator.comparing(s ->
                            s.getEffectiveDate() != null ? s.getEffectiveDate() : LocalDate.MIN))
                    .orElseThrow();

            Payslip p = computePayslip(emp, position, setup, run, summaries.get(emp.getUserId()));
            payslips.add(p);
            total = total.add(p.getNetPay());
        }

        payslipRepo.saveAll(payslips);
        run.setTotalAmount(total);
        run.setEmployeeCount(payslips.size());
        run.setStatus("generated");

        List<PayrollRunStep> steps = stepRepo.findByPayrollRunIdOrderByStepOrder(runId);
        for (int i = 0; i < steps.size() - 1; i++) {
            steps.get(i).setStatus("done");
            steps.get(i).setCompletedAt(LocalDateTime.now().minusMinutes(steps.size() - i));
        }
        steps.get(steps.size() - 1).setStatus("in-progress");
        stepRepo.saveAll(steps);

        return runRepo.save(run);
    }

    @Transactional
    public PayrollRun releaseRun(Long runId) {
        PayrollRun run = runRepo.findById(runId).orElseThrow();
        run.setStatus("released");
        run.setReleasedAt(LocalDateTime.now());

        List<Payslip> payslips = payslipRepo.findByPayrollRunId(runId);
        payslips.forEach(p -> { p.setStatus("released"); p.setReleasedAt(LocalDateTime.now()); });
        payslipRepo.saveAll(payslips);

        List<PayrollRunStep> steps = stepRepo.findByPayrollRunIdOrderByStepOrder(runId);
        steps.forEach(s -> { s.setStatus("done"); s.setCompletedAt(LocalDateTime.now()); });
        stepRepo.saveAll(steps);

        return runRepo.save(run);
    }

    // ── Payslips ──────────────────────────────────────────────────────────────

    public Page<Payslip> getPayslips(Long runId, String status, String search, int page, int size) {
        Long rid = (runId != null && runId == 0) ? null : runId;
        String st = (status == null || status.isBlank()) ? null : status;
        String s  = (search == null || search.isBlank()) ? null : search.trim();
        return payslipRepo.search(rid, st, s, PageRequest.of(page, size, Sort.by("employeeName")));
    }

    public Payslip getPayslip(Long id) {
        return payslipRepo.findById(id).orElseThrow();
    }

    // ── Payslip computation ───────────────────────────────────────────────────

    private Payslip computePayslip(User emp, JobPosition position,
                                   com.gpr.payroll.entity.PayrollSetup setup, PayrollRun run,
                                   com.gpr.kernel.dto.UserSummaryDto empSummary) {
        String basis        = setup.getCompensationBasis();
        BigDecimal monthly  = nvl(setup.getBaseSalary());

        // Basic salary for this pay period
        BigDecimal basic    = periodAmount(monthly, basis, run.getPeriodStart(), run.getPeriodEnd());

        // Allowances (stored in incentives field for display)
        BigDecimal allowances = sumLineItems(setup.getAllowancesJson(), basic);

        // Overtime + holiday/rest-day premium pay from the employee's approved claims for this period,
        // valued at the company's configured multipliers, itemised per type.
        OvertimePayCalculator.Result ot = overtimePayCalculator.compute(
                emp.getUserId(), emp.getCompanyId(), monthly,
                run.getPeriodStart(), run.getPeriodEnd());
        BigDecimal overtimePay = ot.total();

        BigDecimal gross      = basic.add(allowances).add(overtimePay);

        // Statutory deductions — computed on monthly basis, then scaled to pay period
        BigDecimal sss        = scale(computeSss(monthly),             basis);
        BigDecimal phi        = scale(computePhilhealth(monthly),      basis);
        BigDecimal pag        = scale(computePagibig(monthly),         basis);
        BigDecimal taxable    = monthly.subtract(computeSss(monthly))
                                       .subtract(computePhilhealth(monthly))
                                       .subtract(computePagibig(monthly));
        BigDecimal tax        = scale(computeWithholdingTax(taxable),  basis);

        // Named deductions from the setup (e.g. cash advance, company loan)
        BigDecimal named      = sumLineItems(setup.getDeductionsJson(), basic);

        BigDecimal totalDed   = sss.add(phi).add(pag).add(tax).add(named);
        BigDecimal net        = gross.subtract(totalDed).max(BigDecimal.ZERO);

        String employeeName = empSummary == null ? emp.getEmployeeId()
                : ((empSummary.getFirstName() == null ? "" : empSummary.getFirstName())
                        + " " + (empSummary.getLastName() == null ? "" : empSummary.getLastName())).trim();
        return Payslip.builder()
                .payrollRun(run)
                .employeeId(emp.getEmployeeId())
                .employeeName(employeeName)
                .position(position.getTitle())
                .periodStart(run.getPeriodStart())
                .periodEnd(run.getPeriodEnd())
                .basicSalary(basic)
                .incentives(allowances)
                .overtimePay(overtimePay)
                .overtimeBreakdown(ot.lines())
                .grossPay(gross)
                .sss(sss)
                .philhealth(phi)
                .pagibig(pag)
                .tax(tax)
                .totalDeductions(totalDed)
                .netPay(net)
                .status("generated")
                .build();
    }

    // ── Period / scaling helpers ──────────────────────────────────────────────

    /** Compute the salary amount for a given pay period from the monthly rate. */
    private BigDecimal periodAmount(BigDecimal monthly, String basis,
                                    LocalDate start, LocalDate end) {
        return switch (basis != null ? basis : "") {
            case "MONTHLY"      -> monthly;
            case "SEMI_MONTHLY" -> monthly.divide(TWO, 2, RoundingMode.HALF_UP);
            case "WEEKLY"       -> monthly.divide(FOUR_333, 2, RoundingMode.HALF_UP);
            case "DAILY"        -> {
                long days = ChronoUnit.DAYS.between(start, end) + 1;
                yield monthly.divide(TWENTY_TWO, 10, RoundingMode.HALF_UP)
                             .multiply(BigDecimal.valueOf(days))
                             .setScale(2, RoundingMode.HALF_UP);
            }
            // CONTRACTUAL / YEARLY — default to semi-monthly
            default -> monthly.divide(TWO, 2, RoundingMode.HALF_UP);
        };
    }

    /** Scale a monthly deduction/contribution down to the pay period. */
    private BigDecimal scale(BigDecimal monthly, String basis) {
        return switch (basis != null ? basis : "") {
            case "MONTHLY"      -> monthly;
            case "SEMI_MONTHLY" -> monthly.divide(TWO, 2, RoundingMode.HALF_UP);
            case "WEEKLY"       -> monthly.divide(FOUR_333, 2, RoundingMode.HALF_UP);
            case "DAILY"        -> monthly.divide(TWENTY_TWO, 2, RoundingMode.HALF_UP);
            default             -> monthly.divide(TWO, 2, RoundingMode.HALF_UP);
        };
    }

    // ── Statutory deduction formulas (monthly basis) ──────────────────────────

    private BigDecimal computeSss(BigDecimal monthly) {
        return monthly.multiply(SSS_RATE)
                      .setScale(2, RoundingMode.HALF_UP)
                      .min(SSS_MAX).max(SSS_MIN);
    }

    private BigDecimal computePhilhealth(BigDecimal monthly) {
        return monthly.multiply(PHILHEALTH_RATE)
                      .setScale(2, RoundingMode.HALF_UP)
                      .min(PHILHEALTH_MAX).max(PHILHEALTH_MIN);
    }

    private BigDecimal computePagibig(BigDecimal monthly) {
        return monthly.multiply(PAGIBIG_RATE)
                      .setScale(2, RoundingMode.HALF_UP)
                      .min(PAGIBIG_MAX);
    }

    /** Withholding tax per TRAIN Law (monthly taxable income). */
    private BigDecimal computeWithholdingTax(BigDecimal taxable) {
        BigDecimal t = taxable.max(BigDecimal.ZERO);
        if (t.compareTo(new BigDecimal("20833")) <= 0)
            return BigDecimal.ZERO;
        if (t.compareTo(new BigDecimal("33333")) <= 0)
            return t.subtract(new BigDecimal("20833"))
                    .multiply(new BigDecimal("0.20")).setScale(2, RoundingMode.HALF_UP);
        if (t.compareTo(new BigDecimal("66667")) <= 0)
            return new BigDecimal("2500")
                    .add(t.subtract(new BigDecimal("33333")).multiply(new BigDecimal("0.25")))
                    .setScale(2, RoundingMode.HALF_UP);
        if (t.compareTo(new BigDecimal("166667")) <= 0)
            return new BigDecimal("10833")
                    .add(t.subtract(new BigDecimal("66667")).multiply(new BigDecimal("0.30")))
                    .setScale(2, RoundingMode.HALF_UP);
        if (t.compareTo(new BigDecimal("666667")) <= 0)
            return new BigDecimal("40833")
                    .add(t.subtract(new BigDecimal("166667")).multiply(new BigDecimal("0.32")))
                    .setScale(2, RoundingMode.HALF_UP);
        return new BigDecimal("200833")
                .add(t.subtract(new BigDecimal("666667")).multiply(new BigDecimal("0.35")))
                .setScale(2, RoundingMode.HALF_UP);
    }

    // ── JSON line-item parsing ────────────────────────────────────────────────

    private record LineItem(String name, BigDecimal amount, String amountType, String schedule) {}

    private BigDecimal sumLineItems(String json, BigDecimal basic) {
        if (json == null || json.isBlank() || "[]".equals(json.trim())) return BigDecimal.ZERO;
        try {
            LineItem[] items = objectMapper.readValue(json, LineItem[].class);
            BigDecimal total = BigDecimal.ZERO;
            for (LineItem item : items) {
                BigDecimal amt = nvl(item.amount());
                if ("PERCENTAGE".equals(item.amountType())) {
                    amt = basic.multiply(amt.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
                }
                total = total.add(amt);
            }
            return total.setScale(2, RoundingMode.HALF_UP);
        } catch (Exception e) {
            return BigDecimal.ZERO;
        }
    }

    private static BigDecimal nvl(BigDecimal v) { return v != null ? v : BigDecimal.ZERO; }

    // ── PDF ───────────────────────────────────────────────────────────────────

    public byte[] generatePayslipPdf(Long id) {
        Payslip p = getPayslip(id);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            Document doc = new Document();
            PdfWriter.getInstance(doc, baos);
            doc.open();
            doc.add(new Paragraph("PAYSLIP", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 18)));
            doc.add(new Paragraph("Employee: " + p.getEmployeeName()));
            doc.add(new Paragraph("Employee ID: " + p.getEmployeeId()));
            doc.add(new Paragraph("Position: " + p.getPosition()));
            doc.add(new Paragraph("Period: " + p.getPeriodStart() + " to " + p.getPeriodEnd()));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("--- EARNINGS ---", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            doc.add(new Paragraph("Basic Salary: " + p.getBasicSalary()));
            doc.add(new Paragraph("Allowances: " + p.getIncentives()));
            if (p.getOvertimeBreakdown() != null && !p.getOvertimeBreakdown().isEmpty()) {
                for (com.gpr.common.entity.PayslipOvertimeLine line : p.getOvertimeBreakdown()) {
                    doc.add(new Paragraph(OvertimePayCalculator.label(line.getOvertimeType())
                            + " (" + OvertimePayCalculator.hhmm(line.getHours()) + "): " + line.getAmount()));
                }
            } else if (p.getOvertimePay() != null && p.getOvertimePay().signum() > 0) {
                doc.add(new Paragraph("Overtime/Premium Pay: " + p.getOvertimePay()));
            }
            doc.add(new Paragraph("Gross Pay: " + p.getGrossPay()));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("--- DEDUCTIONS ---", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            doc.add(new Paragraph("SSS: " + p.getSss()));
            doc.add(new Paragraph("PhilHealth: " + p.getPhilhealth()));
            doc.add(new Paragraph("Pag-IBIG: " + p.getPagibig()));
            doc.add(new Paragraph("Withholding Tax: " + p.getTax()));
            doc.add(new Paragraph("Absences: " + p.getAbsences()));
            doc.add(new Paragraph("Late Penalties: " + p.getLatePenalties()));
            doc.add(new Paragraph("Cash Advances: " + p.getCashAdvances()));
            doc.add(new Paragraph("Total Deductions: " + p.getTotalDeductions()));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("NET PAY: " + p.getNetPay(), FontFactory.getFont(FontFactory.HELVETICA_BOLD, 14)));
            doc.close();
        } catch (Exception e) {
            throw new RuntimeException("PDF generation failed", e);
        }
        return baos.toByteArray();
    }

    // ── Excel ─────────────────────────────────────────────────────────────────

    public byte[] generatePayslipExcel(Long id) {
        Payslip p = getPayslip(id);
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Payslip");
            int r = 0;
            row(sheet, r++, "PAYSLIP");
            row(sheet, r++, "Employee",    p.getEmployeeName());
            row(sheet, r++, "Employee ID", p.getEmployeeId());
            row(sheet, r++, "Position",    p.getPosition());
            row(sheet, r++, "Period",      p.getPeriodStart() + " to " + p.getPeriodEnd());
            row(sheet, r++, "Team Leader", p.getTeamLeader());
            row(sheet, r++, "");
            row(sheet, r++, "EARNINGS", "");
            row(sheet, r++, "Basic Salary",  p.getBasicSalary().doubleValue());
            row(sheet, r++, "Allowances",    p.getIncentives().doubleValue());
            if (p.getOvertimeBreakdown() != null && !p.getOvertimeBreakdown().isEmpty()) {
                for (com.gpr.common.entity.PayslipOvertimeLine line : p.getOvertimeBreakdown()) {
                    row(sheet, r++, OvertimePayCalculator.label(line.getOvertimeType())
                            + " (" + OvertimePayCalculator.hhmm(line.getHours()) + ")",
                            line.getAmount().doubleValue());
                }
            } else if (p.getOvertimePay() != null && p.getOvertimePay().signum() > 0) {
                row(sheet, r++, "Overtime/Premium Pay", p.getOvertimePay().doubleValue());
            }
            row(sheet, r++, "Gross Pay",     p.getGrossPay().doubleValue());
            row(sheet, r++, "");
            row(sheet, r++, "DEDUCTIONS", "");
            row(sheet, r++, "SSS",              p.getSss().doubleValue());
            row(sheet, r++, "PhilHealth",       p.getPhilhealth().doubleValue());
            row(sheet, r++, "Pag-IBIG",         p.getPagibig().doubleValue());
            row(sheet, r++, "Withholding Tax",  p.getTax().doubleValue());
            row(sheet, r++, "Absences",         p.getAbsences().doubleValue());
            row(sheet, r++, "Late Penalties",   p.getLatePenalties().doubleValue());
            row(sheet, r++, "Cash Advances",    p.getCashAdvances().doubleValue());
            row(sheet, r++, "Total Deductions", p.getTotalDeductions().doubleValue());
            row(sheet, r++, "");
            row(sheet, r++, "NET PAY", p.getNetPay().doubleValue());
            wb.write(baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Excel generation failed", e);
        }
    }

    private void row(Sheet sheet, int rowNum, String... values) {
        Row row = sheet.createRow(rowNum);
        for (int i = 0; i < values.length; i++) row.createCell(i).setCellValue(values[i]);
    }

    private void row(Sheet sheet, int rowNum, String label, double value) {
        Row row = sheet.createRow(rowNum);
        row.createCell(0).setCellValue(label);
        row.createCell(1).setCellValue(value);
    }
}
