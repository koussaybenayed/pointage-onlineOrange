package com.pointage.ticketreport;

import java.util.List;

public record UserTicketStatsDto(
        String userName,
        String team,
        int totalTickets,
        List<TicketDto> tickets
) {
}
