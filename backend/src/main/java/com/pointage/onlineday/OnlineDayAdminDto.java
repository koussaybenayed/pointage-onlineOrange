package com.pointage.onlineday;

import java.time.LocalDate;

public record OnlineDayAdminDto(Long id, String userName, LocalDate dayDate, OnlineDay.Status status) {

    public static OnlineDayAdminDto from(OnlineDay od) {
        return new OnlineDayAdminDto(od.getId(), od.getUser().getFullName(), od.getDayDate(), od.getStatus());
    }
}