package com.pointage.onlineday;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/admin")
public class AdminOnlineDayController {

    private final OnlineDayService onlineDayService;

    public AdminOnlineDayController(OnlineDayService onlineDayService) {
        this.onlineDayService = onlineDayService;
    }

    @GetMapping("/online-days")
    public List<OnlineDayAdminDto> getAllOnlineDays(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start) {
        return onlineDayService.getAllOnlineDays(start);
    }
}