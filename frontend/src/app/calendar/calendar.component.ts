import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { OnlineDayService } from '../services/online-day.service';
import { OnlineDay } from '../models/models';
import { extractError } from '../utils/error.util';

interface DayCell {
  date: Date;
  dateKey: string;
  label: string;
  isToday: boolean;
  isDisabled: boolean;
  disabledReason: string;
  booked?: OnlineDay;
}

@Component({
  selector: 'app-calendar',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './calendar.component.html',
  styleUrls: ['./calendar.component.scss'],
})
export class CalendarComponent implements OnInit {
  weekStart!: Date;
  days: DayCell[] = [];
  bookedThisWeek = 0;
  loading = true;
  submittingId: number | null = null;
  error = '';
  success = '';

  constructor(private onlineDayService: OnlineDayService) {}

  ngOnInit(): void {
    this.initWeek();
    this.loadWeek();
  }

  get weekLabel(): string {
    return this.formatDate(this.weekStart);
  }

  prevWeek(): void {
    this.weekStart = new Date(this.weekStart);
    this.weekStart.setDate(this.weekStart.getDate() - 7);
    this.loadWeek();
  }

  nextWeek(): void {
    this.weekStart = new Date(this.weekStart);
    this.weekStart.setDate(this.weekStart.getDate() + 7);
    this.loadWeek();
  }

  book(day: DayCell): void {
    this.submittingId = -1;
    this.error = '';
    this.success = '';
    const dateKey = day.dateKey;
    this.onlineDayService.bookDay(dateKey).subscribe({
      next: () => {
        this.submittingId = null;
        this.success = `Jour du ${this.formatDate(day.date)} réservé.`;
        this.loadWeek();
      },
      error: (err) => {
        this.submittingId = null;
        this.error = extractError(err);
      },
    });
  }

  cancel(day: DayCell): void {
    const od = day.booked;
    if (!od) return;
    this.submittingId = od.id;
    this.error = '';
    this.success = '';
    this.onlineDayService.cancelDay(od.id).subscribe({
      next: () => {
        this.submittingId = null;
        this.success = `Jour du ${this.formatDate(day.date)} annulé.`;
        this.loadWeek();
      },
      error: (err) => {
        this.submittingId = null;
        this.error = extractError(err);
      },
    });
  }

  canBook(day: DayCell): boolean {
    return !day.isDisabled && !day.booked && !this.hasConsecutive(day);
  }

  private loadWeek(): void {
    this.loading = true;
    this.error = '';
    this.onlineDayService.getMyWeek(this.iso(this.weekStart)).subscribe({
      next: (list) => {
        this.loading = false;
        this.render(list);
      },
      error: (err) => {
        this.loading = false;
        this.error = extractError(err);
      },
    });
  }

  private initWeek(): void {
    const now = new Date();
    const day = now.getDay(); // 0 Sun .. 6 Sat
    const diff = day === 0 ? -6 : 1 - day; // Monday as start
    this.weekStart = new Date(now);
    this.weekStart.setDate(now.getDate() + diff);
    this.weekStart.setHours(0, 0, 0, 0);
  }

  private render(booked: OnlineDay[]): void {
    const bookedByKey = new Map(booked.map((b) => [b.dayDate, b]));
    const start = this.weekStart;
    this.days = [];

    for (let i = 0; i < 7; i++) {
      const d = new Date(start);
      d.setDate(start.getDate() + i);
      const dow = d.getDay();
      const dateKey = this.iso(d);
      const isToday = this.isSameDay(d, new Date());

      const disabled = dow === 5 || dow === 0 || dow === 6 || d.getTime() < new Date(new Date().toDateString()).getTime();
      let reason = '';
      if (dow === 5) reason = 'Vendredi interdit';
      else if (dow === 0 || dow === 6) reason = 'Week-end';
      else if (d.getTime() < new Date(new Date().toDateString()).getTime()) reason = 'Passé';

      const bookedDay = bookedByKey.get(dateKey);
      this.days.push({
        date: d,
        dateKey,
        label: d.toLocaleDateString('fr-FR', { weekday: 'short' }),
        isToday,
        isDisabled: disabled,
        disabledReason: reason,
        booked: bookedDay,
      });
    }
    this.bookedThisWeek = booked.length;
  }

  hasConsecutive(day: DayCell): boolean {
    const prev = new Date(day.date);
    prev.setDate(prev.getDate() - 1);
    const next = new Date(day.date);
    next.setDate(next.getDate() + 1);
    return this.days.some((d) => d.booked && (this.isSameDay(d.date, prev) || this.isSameDay(d.date, next)));
  }

  private isSameDay(a: Date, b: Date): boolean {
    return a.getFullYear() === b.getFullYear() && a.getMonth() === b.getMonth() && a.getDate() === b.getDate();
  }

  private iso(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }

  private formatDate(d: Date): string {
    return d.toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
  }
}