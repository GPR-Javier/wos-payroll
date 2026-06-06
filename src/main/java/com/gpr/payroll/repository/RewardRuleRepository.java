package com.gpr.payroll.repository;

import com.gpr.common.entity.RewardRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRuleRepository extends JpaRepository<RewardRule, Long> {
    List<RewardRule> findAllByOrderByCreatedAtDesc();
    long countByActiveTrue();
}
