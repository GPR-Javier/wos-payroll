package com.gpm.payroll.repository;

import com.gpm.common.entity.RatingVersion;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RatingVersionRepository extends JpaRepository<RatingVersion, Long> {
    List<RatingVersion> findAllByOrderByUploadedAtDesc();
}
