package com.pointage.onlineday;

import com.pointage.user.User;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDate;

@Entity
@Table(name = "online_days",
       uniqueConstraints = @UniqueConstraint(columnNames = {"user_id", "day_date"}))
@Getter
@Setter
@NoArgsConstructor
public class OnlineDay {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "day_date", nullable = false)
    private LocalDate dayDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Status status = Status.BOOKED;

    public enum Status {
        BOOKED,
        WORKED,      // report submitted on time
        MISSED       // report not submitted by deadline -> did not work
    }
}
