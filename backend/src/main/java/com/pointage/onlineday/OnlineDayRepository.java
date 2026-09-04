package com.pointage.onlineday;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface OnlineDayRepository extends JpaRepository<OnlineDay, Long> {

    List<OnlineDay> findByUserIdAndDayDateBetweenOrderByDayDateAsc(Long userId, LocalDate start, LocalDate end);

    List<OnlineDay> findByUserIdAndDayDateBetween(Long userId, LocalDate start, LocalDate end);

    Optional<OnlineDay> findByUserIdAndId(Long userId, Long id);

    List<OnlineDay> findByStatus(OnlineDay.Status status);

    long countByUserIdAndDayDateBetween(Long userId, LocalDate start, LocalDate end);

    @Query("SELECT od FROM OnlineDay od JOIN FETCH od.user WHERE od.dayDate BETWEEN :start AND :end ORDER BY od.user.id, od.dayDate")
    List<OnlineDay> findAllInRangeWithUser(@Param("start") LocalDate start, @Param("end") LocalDate end);
}
