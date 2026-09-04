package com.pointage.ticketreport;

public record TicketAlert(
        String type,
        String userName,
        String message,
        String timestamp
) {
}
