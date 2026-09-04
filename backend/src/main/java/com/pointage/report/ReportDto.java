package com.pointage.report;

import com.pointage.onlineday.OnlineDay;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ReportDto(Long id, Long onlineDayId, LocalDate dayDate, String content,
                        LocalDateTime submittedAt, String userName) {

    public static ReportDto from(Report r, boolean includeUser) {
        return new ReportDto(
                r.getId(),
                r.getOnlineDay().getId(),
                r.getOnlineDay().getDayDate(),
                r.getContent(),
                r.getSubmittedAt(),
                includeUser ? r.getUser().getFullName() : null
        );
    }
}
