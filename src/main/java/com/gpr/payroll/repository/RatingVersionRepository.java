package com.gpr.payroll.repository;

import com.gpr.common.entity.RatingVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingVersionRepository extends JpaRepository<RatingVersion, Long> {
    List<RatingVersion> findAllByOrderByUploadedAtDesc();
}
