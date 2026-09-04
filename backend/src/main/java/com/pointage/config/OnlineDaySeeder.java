package com.pointage.config;

import com.pointage.onlineday.OnlineDay;
import com.pointage.onlineday.OnlineDayRepository;
import com.pointage.user.User;
import com.pointage.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.List;

@Component
@Order(2)
public class OnlineDaySeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final OnlineDayRepository onlineDayRepository;

    public OnlineDaySeeder(UserRepository userRepository, OnlineDayRepository onlineDayRepository) {
        this.userRepository = userRepository;
        this.onlineDayRepository = onlineDayRepository;
    }

    @Override
    public void run(String... args) {
        LocalDate weekStart = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));

        List<User> teamUsers = userRepository.findAll().stream()
                .filter(u -> u.getRole() == User.Role.USER)
                .toList();

        for (User u : teamUsers) {
            long existing = onlineDayRepository.countByUserIdAndDayDateBetween(u.getId(), weekStart, weekStart.plusDays(6));
            if (existing > 0) continue;

            int numDays = 2 + (int) (u.getId() % 3); // 2, 3 or 4 worked days
            for (int i = 0; i < numDays; i++) {
                LocalDate day = nextWorkingDay(weekStart, i);
                OnlineDay od = new OnlineDay();
                od.setUser(u);
                od.setDayDate(day);
                od.setStatus(OnlineDay.Status.WORKED);
                onlineDayRepository.save(od);
            }
        }
    }

    private LocalDate nextWorkingDay(LocalDate weekStart, int index) {
        // distribute over Mon-Thu
        LocalDate day = weekStart.plusDays((index * 1L) % 4);
        if (day.getDayOfWeek() == DayOfWeek.FRIDAY) day = day.plusDays(1);
        if (day.getDayOfWeek() == DayOfWeek.SATURDAY) day = day.plusDays(2);
        if (day.getDayOfWeek() == DayOfWeek.SUNDAY) day = day.plusDays(1);
        return day;
    }
}
