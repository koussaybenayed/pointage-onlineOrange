package com.pointage.ticketreport;

public record TicketDto(
        String ticketNumber,
        String userName,
        String createdDate,
        String resolutionDate,
        String createdDateTime,
        String resolutionDateTime,
        String followUpDate,
        String source
) {
}
