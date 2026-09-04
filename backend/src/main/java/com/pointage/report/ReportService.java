package com.pointage.report;

import com.pointage.onlineday.OnlineDay;
import com.pointage.onlineday.OnlineDayRepository;
import com.pointage.onlineday.OnlineDayService;
import com.pointage.user.CurrentUser;
import com.pointage.user.User;
import com.pointage.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Service
public class ReportService {

    private final ReportRepository reportRepository;
    private final OnlineDayRepository onlineDayRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;

    public ReportService(ReportRepository reportRepository,
                         OnlineDayRepository onlineDayRepository,
                         UserRepository userRepository,
                         CurrentUser currentUser) {
        this.reportRepository = reportRepository;
        this.onlineDayRepository = onlineDayRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
    }

    @Transactional
    public ReportDto submit(ReportRequest request) {
        User user = getCurrentUser();
        OnlineDay od = onlineDayRepository.findByUserIdAndId(user.getId(), request.onlineDayId())
                .orElseThrow(() -> new IllegalArgumentException("Online day not found"));

        if (od.getStatus() != OnlineDay.Status.BOOKED) {
            throw new IllegalArgumentException("Report already processed for this day");
        }
        if (od.getDayDate().isAfter(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot submit report for a future day");
        }
        LocalDateTime deadline = od.getDayDate().atTime(LocalTime.of(18, 0));
        if (LocalDateTime.now().isAfter(deadline)) {
            throw new IllegalArgumentException("Report deadline (6:00 PM) has passed");
        }
        if (request.content() == null || request.content().isBlank()) {
            throw new IllegalArgumentException("Report content is required");
        }
        if (reportRepository.findByOnlineDayId(od.getId()).isPresent()) {
            throw new IllegalArgumentException("Report already submitted for this day");
        }

        Report report = new Report();
        report.setUser(user);
        report.setOnlineDay(od);
        report.setContent(request.content().trim());
        report.setSubmittedAt(LocalDateTime.now());

        od.setStatus(OnlineDay.Status.WORKED);

        return ReportDto.from(reportRepository.save(report), false);
    }

    @Transactional(readOnly = true)
    public List<ReportDto> getMyReports(LocalDate start) {
        User user = getCurrentUser();
        LocalDate weekStart = start != null
                ? start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return reportRepository.findUserInRange(user.getId(), weekStart, weekEnd)
                .stream().map(r -> ReportDto.from(r, false)).toList();
    }

    @Transactional(readOnly = true)
    public List<ReportDto> getAllReports(LocalDate start) {
        LocalDate weekStart = start != null
                ? start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return reportRepository.findAllInRangeWithDetails(weekStart, weekEnd)
                .stream().map(r -> ReportDto.from(r, true)).toList();
    }

    private User getCurrentUser() {
        return userRepository.findByEmail(currentUser.getEmail())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
