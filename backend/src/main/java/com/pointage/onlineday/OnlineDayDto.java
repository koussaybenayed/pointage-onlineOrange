package com.pointage.onlineday;

import java.time.LocalDate;

public record OnlineDayDto(Long id, LocalDate dayDate, OnlineDay.Status status) {

    public static OnlineDayDto from(OnlineDay od) {
        return new OnlineDayDto(od.getId(), od.getDayDate(), od.getStatus());
    }
}
