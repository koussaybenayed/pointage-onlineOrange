package com.pointage.stats;

import com.pointage.onlineday.OnlineDay;
import com.pointage.onlineday.OnlineDayRepository;
import com.pointage.onlineday.OnlineDay.Status;
import com.pointage.ticketreport.TicketReportService;
import com.pointage.user.CurrentUser;
import com.pointage.user.User;
import com.pointage.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class StatsService {

    private final OnlineDayRepository onlineDayRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final TicketReportService ticketReportService;

    public StatsService(OnlineDayRepository onlineDayRepository,
                        UserRepository userRepository,
                        CurrentUser currentUser,
                        TicketReportService ticketReportService) {
        this.onlineDayRepository = onlineDayRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.ticketReportService = ticketReportService;
    }

    @Transactional(readOnly = true)
    public UserWeekStats getMyStats(LocalDate start) {
        User user = userRepository.findByEmail(currentUser.getEmail()).orElseThrow();
        LocalDate weekStart = normalizeWeek(start);
        return buildForUser(user, weekStart);
    }

    @Transactional(readOnly = true)
    public List<UserWeekStats> getAllStats(LocalDate start) {
        LocalDate weekStart = normalizeWeek(start);
        LocalDate weekEnd = weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        List<OnlineDay> days = onlineDayRepository.findAllInRangeWithUser(weekStart, weekEnd);
        Map<Long, List<OnlineDay>> byUser = days.stream()
                .collect(Collectors.groupingBy(od -> od.getUser().getId()));

        Map<String, Integer> ticketCounts = ticketReportService.getUserTicketCountsByFullName(weekStart, weekEnd);

        return userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.USER)
                .map(u -> buildFromDays(u, byUser.getOrDefault(u.getId(), List.of()), weekStart, ticketCounts))
                .sorted(Comparator.comparing(UserWeekStats::userName))
                .toList();
    }

    private UserWeekStats buildForUser(User user, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<OnlineDay> days = onlineDayRepository.findByUserIdAndDayDateBetweenOrderByDayDateAsc(user.getId(), weekStart, weekEnd);
        return buildFromDays(user, days, weekStart, Collections.emptyMap());
    }

    private UserWeekStats buildFromDays(User user, List<OnlineDay> days, LocalDate weekStart,
                                        Map<String, Integer> ticketCounts) {
        int booked = days.size();
        int worked = (int) days.stream().filter(d -> d.getStatus() == Status.WORKED).count();
        int missed = (int) days.stream().filter(d -> d.getStatus() == Status.MISSED).count();
        double ratio = booked == 0 ? 0 : Math.round((worked * 100.0) / booked);
        int tickets = ticketCounts.getOrDefault(user.getFullName(), 0);
        double rendement = worked == 0 ? 0 : Math.round((tickets * 10.0) / worked) / 10.0;
        return new UserWeekStats(user.getId(), user.getFullName(), booked, worked, missed, ratio, rendement, weekStart);
    }

    private LocalDate normalizeWeek(LocalDate start) {
        return start != null
                ? start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}
