package com.pointage.report;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface ReportRepository extends JpaRepository<Report, Long> {

    Optional<Report> findByOnlineDayId(Long onlineDayId);

    List<Report> findByUserIdOrderBySubmittedAtDesc(Long userId);

    @Query("SELECT r FROM Report r JOIN FETCH r.user JOIN FETCH r.onlineDay od " +
           "WHERE od.dayDate BETWEEN :start AND :end ORDER BY r.submittedAt DESC")
    List<Report> findAllInRangeWithDetails(@Param("start") LocalDate start, @Param("end") LocalDate end);

    @Query("SELECT r FROM Report r JOIN FETCH r.onlineDay WHERE r.user.id = :userId " +
           "AND r.onlineDay.dayDate BETWEEN :start AND :end ORDER BY r.onlineDay.dayDate")
    List<Report> findUserInRange(@Param("userId") Long userId,
                                 @Param("start") LocalDate start,
                                 @Param("end") LocalDate end);
}
