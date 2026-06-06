package com.gpr.payroll.service;

import com.gpr.common.entity.RatingVersion;
import com.gpr.common.entity.RewardHistory;
import com.gpr.common.entity.RewardRule;
import com.gpr.common.entity.TeacherRating;
import com.gpr.payroll.repository.RatingVersionRepository;
import com.gpr.payroll.repository.RewardHistoryRepository;
import com.gpr.payroll.repository.RewardRuleRepository;
import com.gpr.payroll.repository.TeacherRatingRepository;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class RewardsService {

    private static final String UPLOAD_DIR = System.getProperty("user.home") + File.separator + "wos-uploads" + File.separator + "ratings";

    private final RewardRuleRepository ruleRepo;
    private final RewardHistoryRepository historyRepo;
    private final RatingVersionRepository ratingVersionRepo;
    private final TeacherRatingRepository ratingRepo;

    public Map<String, Object> getStats() {
        double avgRating = ratingRepo.overallAverageRating();
        List<Object[]> topList = ratingRepo.avgRatingByTeacher();
        String topPerformer = topList.isEmpty() ? "N/A" : (String) topList.get(0)[0];
        long issued = historyRepo.countReleased();
        long pending = historyRepo.countPending();
        long activeRules = ruleRepo.countByActiveTrue();
        return Map.of(
                "averageRating", Math.round(avgRating * 10.0) / 10.0,
                "topPerformerName", topPerformer,
                "totalRewardsIssued", issued,
                "pendingRewardPayouts", pending,
                "activeRulesCount", activeRules
        );
    }

    public Page<Map<String, Object>> getTeacherSummaries(String search, String period, int page, int size) {
        List<Object[]> rows = "weekly".equals(period) ? ratingRepo.avgRatingByTeacherWeekly() : ratingRepo.avgRatingByTeacherMonthly();
        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (Object[] row : rows) {
            String name = (String) row[0];
            double avg = ((Number) row[1]).doubleValue();
            if (search != null && !search.isBlank() && !name.toLowerCase().contains(search.toLowerCase())) {
                rank++;
                continue;
            }
            long total = ratingRepo.countByTeacherName(name);
            Map<String, Object> entry = new LinkedHashMap<>();
            entry.put("id", rank);
            entry.put("teacherId", "TCH-" + String.format("%03d", rank));
            entry.put("teacherName", name);
            entry.put("teamLeader", "TL");
            entry.put("weeklyAvg", avg);
            entry.put("monthlyAvg", avg);
            entry.put("totalSessions", total);
            entry.put("ratingDistribution", buildDistribution(name));
            entry.put("rewardEligible", avg >= 4.0);
            entry.put("totalEarnedRewards", 0);
            entry.put("attendanceRate", 95.0);
            entry.put("rank", rank);
            result.add(entry);
            rank++;
        }
        int start = page * size;
        int end = Math.min(start + size, result.size());
        List<Map<String, Object>> paged = start >= result.size() ? List.of() : result.subList(start, end);
        return new org.springframework.data.domain.PageImpl<>(paged, PageRequest.of(page, size), result.size());
    }

    private Map<String, Object> buildDistribution(String name) {
        return Map.of(
                "one", ratingRepo.countByTeacherNameAndRating(name, 1),
                "two", ratingRepo.countByTeacherNameAndRating(name, 2),
                "three", ratingRepo.countByTeacherNameAndRating(name, 3),
                "four", ratingRepo.countByTeacherNameAndRating(name, 4),
                "five", ratingRepo.countByTeacherNameAndRating(name, 5)
        );
    }

    public List<Map<String, Object>> getLeaderboard(String period) {
        List<Object[]> rows = "weekly".equals(period) ? ratingRepo.avgRatingByTeacherWeekly() : ratingRepo.avgRatingByTeacherMonthly();
        List<Map<String, Object>> result = new ArrayList<>();
        int rank = 1;
        for (Object[] row : rows) {
            String name = (String) row[0];
            double avg = ((Number) row[1]).doubleValue();
            result.add(Map.of(
                    "rank", rank,
                    "teacherId", "TCH-" + String.format("%03d", rank),
                    "teacherName", name,
                    "averageRating", Math.round(avg * 10.0) / 10.0,
                    "totalSessions", ratingRepo.countByTeacherName(name),
                    "totalRewards", 0
            ));
            rank++;
        }
        return result;
    }

    public List<RewardRule> getRules() {
        return ruleRepo.findAllByOrderByCreatedAtDesc();
    }

    @Transactional
    public RewardRule createRule(RewardRule rule) {
        rule.setCreatedBy(SecurityContextHolder.getContext().getAuthentication().getName());
        return ruleRepo.save(rule);
    }

    @Transactional
    public RewardRule updateRule(Long id, RewardRule updated) {
        RewardRule existing = ruleRepo.findById(id).orElseThrow();
        existing.setName(updated.getName());
        existing.setRuleType(updated.getRuleType());
        existing.setRatingMin(updated.getRatingMin());
        existing.setRatingMax(updated.getRatingMax());
        existing.setMinSessions(updated.getMinSessions());
        existing.setMinAttendanceRate(updated.getMinAttendanceRate());
        existing.setRewardType(updated.getRewardType());
        existing.setMonetaryAmount(updated.getMonetaryAmount());
        existing.setMaterialItem(updated.getMaterialItem());
        existing.setTriggerType(updated.getTriggerType());
        existing.setActive(updated.isActive());
        existing.setEffectiveFrom(updated.getEffectiveFrom());
        existing.setEffectiveTo(updated.getEffectiveTo());
        return ruleRepo.save(existing);
    }

    @Transactional
    public RewardRule toggleRule(Long id) {
        RewardRule rule = ruleRepo.findById(id).orElseThrow();
        rule.setActive(!rule.isActive());
        return ruleRepo.save(rule);
    }

    public Page<RewardHistory> getHistory(String rewardType, String status, String search, int page, int size) {
        String rt = (rewardType == null || rewardType.isBlank()) ? null : rewardType;
        String st = (status == null || status.isBlank()) ? null : status;
        String s = (search == null || search.isBlank()) ? null : search.trim();
        return historyRepo.search(rt, st, s, PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "dateIssued")));
    }

    public Map<String, Object> getPublicDashboard() {
        List<Map<String, Object>> monthly = getLeaderboard("monthly");
        List<Map<String, Object>> weekly = getLeaderboard("weekly");
        List<RewardHistory> recent = historyRepo.findTop5ByOrderByDateIssuedDesc();
        Map<String, Object> teacherOfMonth = monthly.isEmpty() ? null : monthly.get(0);
        return Map.of(
                "monthlyLeaderboard", monthly,
                "weeklyLeaderboard", weekly,
                "teacherOfMonth", teacherOfMonth != null ? teacherOfMonth : Map.of(),
                "mostImproved", Map.of("teacherName", "N/A", "improvementPct", 0),
                "recentRewards", recent.stream().map(h -> Map.of(
                        "teacherName", h.getTeacherName(),
                        "rewardType", h.getRewardType(),
                        "monetaryAmount", h.getMonetaryAmount() != null ? h.getMonetaryAmount() : 0,
                        "materialItem", h.getMaterialItem() != null ? h.getMaterialItem() : "",
                        "dateIssued", h.getDateIssued().toString()
                )).toList()
        );
    }

    public List<RatingVersion> getRatingVersions() {
        return ratingVersionRepo.findAllByOrderByUploadedAtDesc();
    }

    @Transactional
    public RatingVersion uploadRatings(MultipartFile file, String title, String notes) throws IOException {
        String uploader = SecurityContextHolder.getContext().getAuthentication().getName();

        File dir = new File(UPLOAD_DIR);
        dir.mkdirs();
        String storedName = System.currentTimeMillis() + "_" + file.getOriginalFilename();
        File stored = new File(dir, storedName);
        try (FileOutputStream fos = new FileOutputStream(stored)) {
            fos.write(file.getBytes());
        }

        RatingVersion version = RatingVersion.builder()
                .title(title)
                .fileName(file.getOriginalFilename())
                .filePath(stored.getAbsolutePath())
                .uploadedAt(LocalDateTime.now())
                .uploadedBy(uploader)
                .notes(notes)
                .status("processing")
                .build();
        version = ratingVersionRepo.save(version);

        try {
            List<TeacherRating> ratings = parseRatingsExcel(stored, version.getId());
            ratingRepo.saveAll(ratings);
            version.setRowCount(ratings.size());
            version.setStatus("active");
        } catch (Exception ex) {
            log.error("Ratings upload parse error", ex);
            version.setStatus("failed");
        }

        return ratingVersionRepo.save(version);
    }

    private List<TeacherRating> parseRatingsExcel(File file, Long versionId) throws IOException {
        List<TeacherRating> list = new ArrayList<>();
        try (FileInputStream fis = new FileInputStream(file);
             Workbook wb = new XSSFWorkbook(fis)) {
            Sheet sheet = wb.getSheetAt(0);
            boolean first = true;
            for (Row row : sheet) {
                if (first) { first = false; continue; }
                String teacherName = cellStr(row, 0);
                if (teacherName == null) continue;
                String dateStr = cellStr(row, 1);
                LocalDate date;
                try { date = dateStr != null ? LocalDate.parse(dateStr) : LocalDate.now(); }
                catch (Exception e) { date = LocalDate.now(); }
                String student = cellStr(row, 2);
                int rating = (int) row.getCell(3, Row.MissingCellPolicy.CREATE_NULL_AS_BLANK).getNumericCellValue();
                String comment = cellStr(row, 4);
                list.add(TeacherRating.builder()
                        .ratingVersionId(versionId)
                        .teacherName(teacherName)
                        .sessionDate(date)
                        .studentName(student)
                        .rating(Math.max(1, Math.min(5, rating)))
                        .comment(comment)
                        .build());
            }
        }
        return list;
    }

    private String cellStr(Row row, int col) {
        Cell c = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (c == null) return null;
        if (c.getCellType() == CellType.NUMERIC) return String.valueOf((long) c.getNumericCellValue());
        String v = c.getStringCellValue().trim();
        return v.isEmpty() ? null : v;
    }
}
