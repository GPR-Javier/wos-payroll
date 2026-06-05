package com.gpm.payroll.repository;

import com.gpm.common.entity.TeacherRating;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface TeacherRatingRepository extends JpaRepository<TeacherRating, Long> {
    List<TeacherRating> findByRatingVersionId(Long versionId);
    void deleteByRatingVersionId(Long versionId);

    @Query("SELECT tr.teacherName, AVG(tr.rating) FROM TeacherRating tr GROUP BY tr.teacherName ORDER BY AVG(tr.rating) DESC")
    List<Object[]> avgRatingByTeacher();

    @Query("SELECT tr.teacherName, AVG(tr.rating) FROM TeacherRating tr " +
           "WHERE tr.sessionDate >= CURRENT_DATE - 7 day GROUP BY tr.teacherName ORDER BY AVG(tr.rating) DESC")
    List<Object[]> avgRatingByTeacherWeekly();

    @Query("SELECT tr.teacherName, AVG(tr.rating) FROM TeacherRating tr " +
           "WHERE tr.sessionDate >= CURRENT_DATE - 30 day GROUP BY tr.teacherName ORDER BY AVG(tr.rating) DESC")
    List<Object[]> avgRatingByTeacherMonthly();

    @Query("SELECT COALESCE(AVG(tr.rating), 0.0) FROM TeacherRating tr")
    double overallAverageRating();

    @Query("SELECT COUNT(tr) FROM TeacherRating tr WHERE tr.rating = :stars AND tr.teacherName = :name")
    long countByTeacherNameAndRating(@Param("name") String name, @Param("stars") int stars);

    @Query("SELECT COUNT(tr) FROM TeacherRating tr WHERE tr.teacherName = :name")
    long countByTeacherName(@Param("name") String name);
}
