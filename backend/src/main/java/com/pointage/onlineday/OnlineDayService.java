package com.pointage.onlineday;

import com.pointage.ticketreport.TicketReportService;
import com.pointage.user.CurrentUser;
import com.pointage.user.User;
import com.pointage.user.UserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class OnlineDayService {

    private final OnlineDayRepository onlineDayRepository;
    private final UserRepository userRepository;
    private final CurrentUser currentUser;
    private final TicketReportService ticketReportService;

    public OnlineDayService(OnlineDayRepository onlineDayRepository,
                            UserRepository userRepository,
                            CurrentUser currentUser,
                            TicketReportService ticketReportService) {
        this.onlineDayRepository = onlineDayRepository;
        this.userRepository = userRepository;
        this.currentUser = currentUser;
        this.ticketReportService = ticketReportService;
    }

    @Transactional
    public OnlineDayDto bookDay(LocalDate dayDate) {
        User user = getCurrentUser();
        return bookForUser(user, dayDate);
    }

    @Transactional(readOnly = true)
    public List<OnlineDayDto> getMyWeek(LocalDate start) {
        User user = getCurrentUser();
        LocalDate weekStart = start != null
                ? start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = weekStart.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));
        return onlineDayRepository.findByUserIdAndDayDateBetweenOrderByDayDateAsc(user.getId(), weekStart, weekEnd)
                .stream().map(OnlineDayDto::from).toList();
    }

    @Transactional(readOnly = true)
    public List<OnlineDayAdminDto> getAllOnlineDays(LocalDate start) {
        LocalDate monthStart = start != null
                ? start.withDayOfMonth(1)
                : LocalDate.now().withDayOfMonth(1);
        LocalDate monthEnd = monthStart.with(TemporalAdjusters.lastDayOfMonth());
        List<OnlineDay> days = onlineDayRepository.findAllInRangeWithUser(monthStart, monthEnd);

        LocalDate today = LocalDate.now();
        Map<String, Set<LocalDate>> resolved = ticketReportService.getUserResolvedDays(monthStart, monthEnd);

        return days.stream()
                .map(od -> new OnlineDayAdminDto(
                        od.getId(),
                        od.getUser().getFullName(),
                        od.getDayDate(),
                        computedStatus(od.getUser().getFullName(), od.getDayDate(), today,
                                resolved.getOrDefault(od.getUser().getFullName(), Set.of()))))
                .toList();
    }

    private OnlineDay.Status computedStatus(String fullName, LocalDate dayDate, LocalDate today,
                                            Set<LocalDate> resolvedDays) {
        if (dayDate.isAfter(today)) {
            return OnlineDay.Status.BOOKED;
        }
        if (resolvedDays.contains(dayDate)) {
            return OnlineDay.Status.WORKED;
        }
        return OnlineDay.Status.MISSED;
    }

    @Transactional
    public OnlineDayDto adminBookDay(Long userId, LocalDate dayDate) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new IllegalArgumentException("User not found"));
        return bookForUser(user, dayDate);
    }

    @Transactional
    public void adminCancelDay(Long userId, Long onlineDayId) {
        OnlineDay od = onlineDayRepository.findByUserIdAndId(userId, onlineDayId)
                .orElseThrow(() -> new IllegalArgumentException("Online day not found"));
        if (od.getStatus() != OnlineDay.Status.BOOKED) {
            throw new IllegalArgumentException("Cannot cancel a day already processed");
        }
        if (od.getDayDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot cancel a past day");
        }
        onlineDayRepository.delete(od);
    }

    private OnlineDayDto bookForUser(User user, LocalDate dayDate) {
        if (dayDate == null) {
            throw new IllegalArgumentException("Day date is required");
        }
        if (dayDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot book a past day");
        }
        if (dayDate.getDayOfWeek() == DayOfWeek.FRIDAY) {
            throw new IllegalArgumentException("Friday cannot be an online day");
        }
        if (dayDate.getDayOfWeek() == DayOfWeek.SATURDAY || dayDate.getDayOfWeek() == DayOfWeek.SUNDAY) {
            throw new IllegalArgumentException("Weekend days are not allowed");
        }

        LocalDate weekStart = dayDate.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate weekEnd = dayDate.with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY));

        long bookedThisWeek = onlineDayRepository.countByUserIdAndDayDateBetween(user.getId(), weekStart, weekEnd);
        if (bookedThisWeek >= 2) {
            throw new IllegalArgumentException("Maximum of 2 online days per week reached");
        }

        if (onlineDayRepository.findByUserIdAndDayDateBetween(user.getId(), dayDate.minusDays(1), dayDate.plusDays(1))
                .stream().anyMatch(od -> !od.getDayDate().equals(dayDate))) {
            throw new IllegalArgumentException("Consecutive online days are not allowed");
        }

        if (onlineDayRepository.findByUserIdAndDayDateBetween(user.getId(), dayDate, dayDate).stream()
                .findFirst().isPresent()) {
            throw new IllegalArgumentException("This day is already booked");
        }

        OnlineDay onlineDay = new OnlineDay();
        onlineDay.setUser(user);
        onlineDay.setDayDate(dayDate);
        onlineDay.setStatus(OnlineDay.Status.BOOKED);
        return OnlineDayDto.from(onlineDayRepository.save(onlineDay));
    }

    @Transactional
    public void cancelDay(Long id) {
        User user = getCurrentUser();
        OnlineDay od = onlineDayRepository.findByUserIdAndId(user.getId(), id)
                .orElseThrow(() -> new IllegalArgumentException("Online day not found"));
        if (od.getStatus() != OnlineDay.Status.BOOKED) {
            throw new IllegalArgumentException("Cannot cancel a day already processed");
        }
        if (od.getDayDate().isBefore(LocalDate.now())) {
            throw new IllegalArgumentException("Cannot cancel a past day");
        }
        onlineDayRepository.delete(od);
    }

    private User getCurrentUser() {
        return userRepository.findByEmail(currentUser.getEmail())
                .orElseThrow(() -> new IllegalStateException("User not found"));
    }
}
