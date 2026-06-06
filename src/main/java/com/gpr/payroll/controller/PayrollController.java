package com.gpr.payroll.controller;

import com.gpr.common.entity.PayrollRun;
import com.gpr.common.entity.PayrollRunStep;
import com.gpr.common.entity.Payslip;
import com.gpr.payroll.service.PayrollService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/payroll")
@RequiredArgsConstructor
public class PayrollController {
    private final PayrollService payrollService;

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(payrollService.getStats());
    }

    @GetMapping("/runs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<PayrollRun>> getRuns(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(payrollService.getRuns(page, size));
    }

    @PostMapping("/runs")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PayrollRun> createRun(@RequestBody Map<String, String> body) {
        LocalDate start = LocalDate.parse(body.get("periodStart"));
        LocalDate end = LocalDate.parse(body.get("periodEnd"));
        return ResponseEntity.ok(payrollService.createRun(start, end));
    }

    @GetMapping("/runs/{id}/steps")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<PayrollRunStep>> getSteps(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.getSteps(id));
    }

    @PostMapping("/runs/{id}/process")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PayrollRun> processRun(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.processRun(id));
    }

    @PostMapping("/runs/{id}/release")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<PayrollRun> releaseRun(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.releaseRun(id));
    }

    @GetMapping("/payslips")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<Payslip>> getPayslips(
            @RequestParam(required = false) Long runId,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(payrollService.getPayslips(runId, status, search, page, size));
    }

    @GetMapping("/payslips/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Payslip> getPayslip(@PathVariable Long id) {
        return ResponseEntity.ok(payrollService.getPayslip(id));
    }

    @GetMapping("/payslips/{id}/pdf")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payslip-" + id + ".pdf\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(payrollService.generatePayslipPdf(id));
    }

    @GetMapping("/payslips/{id}/excel")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<byte[]> downloadExcel(@PathVariable Long id) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"payslip-" + id + ".xlsx\"")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(payrollService.generatePayslipExcel(id));
    }
}
