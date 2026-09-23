package com.pointage.stats;

import com.pointage.onlineday.OnlineDay;
import com.pointage.onlineday.OnlineDayRepository;
import com.pointage.onlineday.OnlineDay.Status;
import com.pointage.ticketreport.TicketReportService;
import com.pointage.user.CurrentUser;
import com.pointage.user.OfficialTeam;
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
        LocalDate weekEnd = weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        List<OnlineDay> days = onlineDayRepository.findByUserIdAndDayDateBetweenOrderByDayDateAsc(user.getId(), weekStart, weekEnd);
        Map<String, Set<LocalDate>> resolved = ticketReportService.getUserResolvedDays(weekStart, weekEnd);
        return buildFromDays(user, days, weekStart, resolved.getOrDefault(user.getFullName(), Set.of()));
    }

    @Transactional(readOnly = true)
    public List<UserWeekStats> getAllStats(LocalDate start) {
        LocalDate monthStart = normalizeMonth(start);
        LocalDate monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());

        List<OnlineDay> days = onlineDayRepository.findAllInRangeWithUser(monthStart, monthEnd);
        Map<Long, List<OnlineDay>> byUser = days.stream()
                .collect(Collectors.groupingBy(od -> od.getUser().getId()));

        Map<String, Set<LocalDate>> resolvedDays = ticketReportService.getUserResolvedDays(monthStart, monthEnd);

        List<User> officials = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.USER)
                .filter(u -> OfficialTeam.isOfficial(u.getTeam(), u.getFullName()))
                .toList();

        return officials.stream()
                .map(u -> buildFromDays(u, byUser.getOrDefault(u.getId(), List.of()), monthStart,
                        resolvedDays.getOrDefault(u.getFullName(), Set.of())))
                .sorted(Comparator.comparing(UserWeekStats::userName))
                .toList();
    }

    private UserWeekStats buildFromDays(User user, List<OnlineDay> days, LocalDate periodStart,
                                        Set<LocalDate> resolvedDays) {
        int booked = days.size();
        LocalDate today = LocalDate.now();
        int worked = 0;
        int missed = 0;

        List<OnlineDay> workedDays = new ArrayList<>();
        for (OnlineDay od : days) {
            if (od.getDayDate().isAfter(today)) {
                continue;
            }
            if (resolvedDays.contains(od.getDayDate())) {
                worked++;
                workedDays.add(od);
            } else {
                missed++;
            }
        }

        double ratio = booked == 0 ? 0 : Math.round((worked * 100.0) / booked);
        int tickets = getUserTicketCount(user, periodStart);
        double rendement = worked == 0 ? 0 : Math.round((tickets * 10.0) / worked) / 10.0;

        return new UserWeekStats(user.getId(), user.getFullName(), booked, worked, missed, ratio, rendement, periodStart);
    }

    private int getUserTicketCount(User user, LocalDate periodStart) {
        LocalDate periodEnd = periodStart.with(TemporalAdjusters.lastDayOfMonth());
        return ticketReportService.getResolvedDaysForUser(user.getTeam().name(), user.getFullName(), periodStart, periodEnd).size();
    }

    private LocalDate normalizeMonth(LocalDate start) {
        return start != null
                ? start.withDayOfMonth(1)
                : LocalDate.now().withDayOfMonth(1);
    }

    private LocalDate normalizeWeek(LocalDate start) {
        return start != null
                ? start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
    }
}