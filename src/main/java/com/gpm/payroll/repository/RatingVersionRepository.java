package com.gpm.payroll.repository;

import com.gpm.common.entity.RatingVersion;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface RatingVersionRepository extends JpaRepository<RatingVersion, Long> {
    List<RatingVersion> findAllByOrderByUploadedAtDesc();
}
