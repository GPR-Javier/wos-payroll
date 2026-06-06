package com.gpr.payroll.controller;

import com.gpr.common.entity.RatingVersion;
import com.gpr.common.entity.RewardHistory;
import com.gpr.common.entity.RewardRule;
import com.gpr.payroll.service.RewardsService;
import java.io.IOException;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/rewards")
@RequiredArgsConstructor
public class RewardsController {
    private final RewardsService rewardsService;

    @GetMapping("/stats")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Map<String, Object>> stats() {
        return ResponseEntity.ok(rewardsService.getStats());
    }

    @GetMapping("/teachers")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<Map<String, Object>>> teachers(
            @RequestParam(required = false) String search,
            @RequestParam(required = false) String period,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(rewardsService.getTeacherSummaries(search, period, page, size));
    }

    @GetMapping("/leaderboard")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<Map<String, Object>>> leaderboard(
            @RequestParam(defaultValue = "monthly") String period) {
        return ResponseEntity.ok(rewardsService.getLeaderboard(period));
    }

    @GetMapping("/rules")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RewardRule>> getRules() {
        return ResponseEntity.ok(rewardsService.getRules());
    }

    @PostMapping("/rules")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RewardRule> createRule(@RequestBody RewardRule rule) {
        return ResponseEntity.ok(rewardsService.createRule(rule));
    }

    @PutMapping("/rules/{id}")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RewardRule> updateRule(@PathVariable Long id, @RequestBody RewardRule rule) {
        return ResponseEntity.ok(rewardsService.updateRule(id, rule));
    }

    @PatchMapping("/rules/{id}/toggle")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RewardRule> toggleRule(@PathVariable Long id) {
        return ResponseEntity.ok(rewardsService.toggleRule(id));
    }

    @GetMapping("/history")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<Page<RewardHistory>> getHistory(
            @RequestParam(required = false) String rewardType,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "25") int size) {
        return ResponseEntity.ok(rewardsService.getHistory(rewardType, status, search, page, size));
    }

    @GetMapping("/public")
    @PreAuthorize("permitAll()")
    public ResponseEntity<Map<String, Object>> publicDashboard() {
        return ResponseEntity.ok(rewardsService.getPublicDashboard());
    }

    @GetMapping("/ratings/versions")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<List<RatingVersion>> getRatingVersions() {
        return ResponseEntity.ok(rewardsService.getRatingVersions());
    }

    @PostMapping("/ratings/upload")
    @PreAuthorize("isAuthenticated()")
    public ResponseEntity<RatingVersion> uploadRatings(
            @RequestParam("file") MultipartFile file,
            @RequestParam("title") String title,
            @RequestParam(value = "notes", required = false) String notes) throws IOException {
        return ResponseEntity.ok(rewardsService.uploadRatings(file, title, notes));
    }
}
