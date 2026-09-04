import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormBuilder, FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { ReportService } from '../services/report.service';
import { OnlineDayService } from '../services/online-day.service';
import { OnlineDay, Report } from '../models/models';
import { extractError } from '../utils/error.util';

@Component({
  selector: 'app-reports',
  standalone: true,
  imports: [CommonModule, ReactiveFormsModule],
  templateUrl: './reports.component.html',
  styleUrls: ['./reports.component.scss'],
})
export class ReportsComponent implements OnInit {
  onlineDays: OnlineDay[] = [];
  myReports: Report[] = [];
  selectedDay: OnlineDay | null = null;
  loading = true;
  submitting = false;
  error = '';
  success = '';
  deadlinePassedToday = false;

  form: FormGroup<{
    content: FormControl<string | null>;
  }>;

  weekStart!: Date;

  constructor(
    private fb: FormBuilder,
    private reportService: ReportService,
    private onlineDayService: OnlineDayService
  ) {
    this.form = this.fb.group({
      content: ['', [Validators.required, Validators.minLength(10)]],
    });
  }

  ngOnInit(): void {
    this.initWeek();
    this.loadAll();
  }

  get weekLabel(): string {
    return this.weekStart.toLocaleDateString('fr-FR', { day: 'numeric', month: 'long', year: 'numeric' });
  }

  selectDay(od: OnlineDay): void {
    this.selectedDay = od;
    this.error = '';
    this.success = '';
  }

  onSubmit(): void {
    if (this.form.invalid || !this.selectedDay) {
      this.form.markAllAsTouched();
      return;
    }
    this.submitting = true;
    this.error = '';
    this.success = '';
    this.reportService.submit(this.selectedDay.id, this.form.value.content!).subscribe({
      next: () => {
        this.submitting = false;
        this.success = `Rapport du ${this.formatDay(this.selectedDay!.dayDate)} soumis.`;
        this.form.reset();
        this.selectedDay = null;
        this.loadAll();
      },
      error: (err) => {
        this.submitting = false;
        this.error = extractError(err);
      },
    });
  }

  canSubmit(od: OnlineDay): boolean {
    if (od.status !== 'BOOKED') return false;
    if (this.deadlinePassedToday && this.isToday(od)) return false;
    return true;
  }

  private loadAll(): void {
    this.loading = true;
    this.onlineDayService.getMyWeek(this.iso(this.weekStart)).subscribe({
      next: (days) => {
        this.onlineDays = days;
        this.reportService.getMy(this.iso(this.weekStart)).subscribe({
          next: (reports) => {
            this.myReports = reports;
            this.loading = false;
            this.deadlinePassedToday = new Date().getHours() >= 18;
          },
          error: (err) => {
            this.loading = false;
            this.error = extractError(err);
          },
        });
      },
      error: (err) => {
        this.loading = false;
        this.error = extractError(err);
      },
    });
  }

  hasReport(od: OnlineDay): boolean {
    return this.myReports.some((r) => r.onlineDayId === od.id);
  }

  formatDay(dateStr: string): string {
    return new Date(dateStr + 'T00:00:00').toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long' });
  }

  isToday(od: OnlineDay): boolean {
    const today = new Date();
    const d = new Date(od.dayDate + 'T00:00:00');
    return d.getFullYear() === today.getFullYear() && d.getMonth() === today.getMonth() && d.getDate() === today.getDate();
  }

  private initWeek(): void {
    const now = new Date();
    const day = now.getDay();
    const diff = day === 0 ? -6 : 1 - day;
    this.weekStart = new Date(now);
    this.weekStart.setDate(now.getDate() + diff);
    this.weekStart.setHours(0, 0, 0, 0);
  }

  private iso(d: Date): string {
    const y = d.getFullYear();
    const m = String(d.getMonth() + 1).padStart(2, '0');
    const day = String(d.getDate()).padStart(2, '0');
    return `${y}-${m}-${day}`;
  }
}