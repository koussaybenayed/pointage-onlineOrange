package com.pointage.ticketreport;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/admin")
public class AdminTicketReportController {

    private final TicketReportService ticketReportService;

    public AdminTicketReportController(TicketReportService ticketReportService) {
        this.ticketReportService = ticketReportService;
    }

    @GetMapping("/ticket-reports")
    public TicketReportResponse getTicketReport(
            @RequestParam String team,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {

        if (start == null) {
            LocalDate now = LocalDate.now();
            start = now.with(java.time.DayOfWeek.MONDAY);
        }
        if (end == null) {
            end = start.plusDays(6);
        }

        return ticketReportService.getTicketReport(team, start, end);
    }
}
