package com.pointage.scheduler;

import com.pointage.onlineday.OnlineDay;
import com.pointage.onlineday.OnlineDayRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;

@Component
public class ReportDeadlineScheduler {

    private static final Logger log = LoggerFactory.getLogger(ReportDeadlineScheduler.class);

    private final OnlineDayRepository onlineDayRepository;

    public ReportDeadlineScheduler(OnlineDayRepository onlineDayRepository) {
        this.onlineDayRepository = onlineDayRepository;
    }

    // Runs every day at 18:05 Africa/Tunis (a small buffer after the 18:00 deadline)
    // The JVM is configured with the Africa/Tunis zone via spring.jackson.time-zone,
    // but a robust deploy sets server.time-zone too or runs the JVM with that TZ.
    @Scheduled(cron = "0 5 18 * * *", zone = "Africa/Tunis")
    @Transactional
    public void markMissedReports() {
        LocalDate today = LocalDate.now();
        List<OnlineDay> pending = onlineDayRepository.findByStatus(OnlineDay.Status.BOOKED).stream()
                .filter(od -> od.getDayDate().equals(today) || od.getDayDate().isBefore(today))
                .toList();

        int count = 0;
        for (OnlineDay od : pending) {
            od.setStatus(OnlineDay.Status.MISSED);
            count++;
        }
        if (count > 0) {
            log.info("Marked {} online day(s) as MISSED for {}", count, today);
        }
    }
}
