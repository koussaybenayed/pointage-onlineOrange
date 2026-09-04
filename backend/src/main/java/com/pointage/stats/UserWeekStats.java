package com.pointage.stats;

import com.pointage.onlineday.OnlineDay;
import com.pointage.onlineday.OnlineDay.Status;

import java.time.LocalDate;

public record UserWeekStats(Long userId, String userName, int bookedDays,
                            int workedDays, int missedDays,
                            double ratio, double rendement, LocalDate weekStart) {
}
