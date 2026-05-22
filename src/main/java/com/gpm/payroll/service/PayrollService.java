package com.gpm.payroll.service;

import com.gpm.common.entity.PayrollRun;
import com.gpm.common.entity.PayrollRunStep;
import com.gpm.common.entity.Payslip;
import com.gpm.common.entity.User;
import com.gpm.payroll.repository.PayrollRunRepository;
import com.gpm.payroll.repository.PayrollRunStepRepository;
import com.gpm.payroll.repository.PayslipRepository;
import com.gpm.payroll.repository.UserRepository;
import com.lowagie.text.Document;
import com.lowagie.text.FontFactory;
import com.lowagie.text.Paragraph;
import com.lowagie.text.pdf.PdfWriter;
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

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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

    /** UserRole name that identifies a paid teacher in the org. */
    private static final String TEACHER_ROLE = "Teacher";

    private final PayrollRunRepository runRepo;
    private final PayrollRunStepRepository stepRepo;
    private final PayslipRepository payslipRepo;
    private final UserRepository userRepo;

    public Map<String, Object> getStats() {
        BigDecimal released = runRepo.sumReleasedPayroll();
        BigDecimal pending = runRepo.sumPendingProcessing();
        BigDecimal incentives = payslipRepo.sumReleasedIncentives();
        long pendingCount = runRepo.countPending();

        return Map.of(
                "totalPayrollAmount", released.add(pending),
                "pendingProcessing", pendingCount,
                "releasedPayroll", released,
                "incentivesTotal", incentives,
                "totalBasicSalary", released,
                "totalIncentives", incentives,
                "totalAbsences", BigDecimal.ZERO,
                "totalLatePenalties", BigDecimal.ZERO,
                "totalCashAdvances", BigDecimal.ZERO,
                "totalStatutoryDeductions", BigDecimal.ZERO
        );
    }

    public Page<PayrollRun> getRuns(int page, int size) {
        return runRepo.findAllByOrderByCreatedAtDesc(PageRequest.of(page, size));
    }

    @Transactional
    public PayrollRun createRun(LocalDate periodStart, LocalDate periodEnd) {
        String creator = SecurityContextHolder.getContext().getAuthentication().getName();
        String period = periodStart.format(DateTimeFormatter.ofPattern("MMMM yyyy"));

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

        List<User> teachers = userRepo.findActiveByRoleName(TEACHER_ROLE);
        List<Payslip> payslips = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (User t : teachers) {
            BigDecimal basic = BigDecimal.valueOf(15000);
            // Incentives will be derived from TeacherRating once that's wired up;
            // 0 keeps payroll runnable in the interim.
            BigDecimal incentives = BigDecimal.ZERO;
            BigDecimal gross = basic.add(incentives);
            BigDecimal deductions = BigDecimal.ZERO;
            BigDecimal net = gross.subtract(deductions);

            Payslip p = Payslip.builder()
                    .payrollRun(run)
                    .employeeId(t.getEmployeeId())
                    .employeeName(t.getFirstName() + " " + t.getLastName())
                    .position("Teacher")
                    .periodStart(run.getPeriodStart())
                    .periodEnd(run.getPeriodEnd())
                    .basicSalary(basic)
                    .incentives(incentives)
                    .grossPay(gross)
                    .totalDeductions(deductions)
                    .netPay(net)
                    .status("generated")
                    .build();
            payslips.add(p);
            total = total.add(net);
        }
        payslipRepo.saveAll(payslips);
        run.setTotalAmount(total);
        run.setEmployeeCount(teachers.size());
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

    public Page<Payslip> getPayslips(Long runId, String status, String search, int page, int size) {
        Long rid = (runId != null && runId == 0) ? null : runId;
        String st = (status == null || status.isBlank()) ? null : status;
        String s = (search == null || search.isBlank()) ? null : search.trim();
        return payslipRepo.search(rid, st, s, PageRequest.of(page, size, Sort.by("employeeName")));
    }

    public Payslip getPayslip(Long id) {
        return payslipRepo.findById(id).orElseThrow();
    }

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
            doc.add(new Paragraph("Period: " + p.getPeriodStart() + " to " + p.getPeriodEnd()));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("--- EARNINGS ---", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
            doc.add(new Paragraph("Basic Salary: " + p.getBasicSalary()));
            doc.add(new Paragraph("Incentives: " + p.getIncentives()));
            doc.add(new Paragraph("Gross Pay: " + p.getGrossPay()));
            doc.add(new Paragraph(" "));
            doc.add(new Paragraph("--- DEDUCTIONS ---", FontFactory.getFont(FontFactory.HELVETICA_BOLD, 12)));
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

    public byte[] generatePayslipExcel(Long id) {
        Payslip p = getPayslip(id);
        try (XSSFWorkbook wb = new XSSFWorkbook();
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            Sheet sheet = wb.createSheet("Payslip");
            int r = 0;
            row(sheet, r++, "PAYSLIP");
            row(sheet, r++, "Employee", p.getEmployeeName());
            row(sheet, r++, "Employee ID", p.getEmployeeId());
            row(sheet, r++, "Period", p.getPeriodStart() + " to " + p.getPeriodEnd());
            row(sheet, r++, "Team Leader", p.getTeamLeader());
            row(sheet, r++, "");
            row(sheet, r++, "EARNINGS", "");
            row(sheet, r++, "Basic Salary", p.getBasicSalary().doubleValue());
            row(sheet, r++, "Incentives", p.getIncentives().doubleValue());
            row(sheet, r++, "Gross Pay", p.getGrossPay().doubleValue());
            row(sheet, r++, "");
            row(sheet, r++, "DEDUCTIONS", "");
            row(sheet, r++, "Absences", p.getAbsences().doubleValue());
            row(sheet, r++, "Late Penalties", p.getLatePenalties().doubleValue());
            row(sheet, r++, "Cash Advances", p.getCashAdvances().doubleValue());
            row(sheet, r++, "SSS", p.getSss().doubleValue());
            row(sheet, r++, "PhilHealth", p.getPhilhealth().doubleValue());
            row(sheet, r++, "Pag-IBIG", p.getPagibig().doubleValue());
            row(sheet, r++, "Tax", p.getTax().doubleValue());
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
