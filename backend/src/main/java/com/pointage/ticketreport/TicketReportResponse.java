package com.pointage.ticketreport;

import java.util.List;

public record TicketReportResponse(
        List<UserTicketStatsDto> userStats,
        List<TicketAlert> alerts
) {
}
