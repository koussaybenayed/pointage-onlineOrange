package com.pointage.onlineday;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/online-days")
public class OnlineDayController {

    private final OnlineDayService onlineDayService;

    public OnlineDayController(OnlineDayService onlineDayService) {
        this.onlineDayService = onlineDayService;
    }

    @PostMapping
    public ResponseEntity<OnlineDayDto> book(@RequestBody OnlineDayRequest request) {
        return ResponseEntity.ok(onlineDayService.bookDay(request.dayDate()));
    }

    @GetMapping
    public List<OnlineDayDto> getMyWeek(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start) {
        return onlineDayService.getMyWeek(start);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        onlineDayService.cancelDay(id);
        return ResponseEntity.noContent().build();
    }
}
