package com.gpm.payroll.repository;

import com.gpm.common.entity.RewardRule;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RewardRuleRepository extends JpaRepository<RewardRule, Long> {
    List<RewardRule> findAllByOrderByCreatedAtDesc();
    long countByActiveTrue();
}
