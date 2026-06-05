package com.gpm.payroll.repository;

import com.gpm.common.entity.RewardHistory;
import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RewardHistoryRepository extends JpaRepository<RewardHistory, Long> {
    @Query("SELECT h FROM RewardHistory h WHERE " +
           "(:rewardType IS NULL OR h.rewardType = :rewardType) " +
           "AND (:status IS NULL OR h.status = :status) " +
           "AND (:search IS NULL OR LOWER(h.teacherName) LIKE LOWER(CONCAT('%',:search,'%')))")
    Page<RewardHistory> search(@Param("rewardType") String rewardType,
                                @Param("status") String status,
                                @Param("search") String search,
                                Pageable pageable);

    @Query("SELECT COALESCE(COUNT(h),0) FROM RewardHistory h WHERE h.status = 'released'")
    long countReleased();

    @Query("SELECT COALESCE(COUNT(h),0) FROM RewardHistory h WHERE h.status = 'pending'")
    long countPending();

    List<RewardHistory> findTop5ByOrderByDateIssuedDesc();
}
