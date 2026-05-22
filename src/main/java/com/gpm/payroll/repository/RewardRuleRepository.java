package com.gpm.payroll.repository;

import com.gpm.common.entity.RewardRule;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RewardRuleRepository extends JpaRepository<RewardRule, Long> {
    List<RewardRule> findAllByOrderByCreatedAtDesc();
    long countByActiveTrue();
}
