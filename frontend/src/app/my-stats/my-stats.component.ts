import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { StatsService } from '../services/stats.service';
import { UserWeekStats } from '../models/models';
import { extractError } from '../utils/error.util';

@Component({
  selector: 'app-my-stats',
  standalone: true,
  imports: [CommonModule],
  templateUrl: './my-stats.component.html',
  styleUrls: ['./my-stats.component.scss'],
})
export class MyStatsComponent implements OnInit {
  stats: UserWeekStats | null = null;
  loading = true;
  error = '';

  constructor(private statsService: StatsService) {}

  ngOnInit(): void {
    this.statsService.getMyStats().subscribe({
      next: (s) => {
        this.stats = s;
        this.loading = false;
      },
      error: (err) => {
        this.error = extractError(err);
        this.loading = false;
      },
    });
  }

  previousWeek(): void {
    const d = new Date(this.stats!.weekStart + 'T00:00:00');
    d.setDate(d.getDate() - 7);
    this.fetch(d);
  }

  nextWeek(): void {
    const d = new Date(this.stats!.weekStart + 'T00:00:00');
    d.setDate(d.getDate() + 7);
    this.fetch(d);
  }

  private fetch(d: Date): void {
    this.loading = true;
    this.error = '';
    const iso = `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
    this.statsService.getMyStats(iso).subscribe({
      next: (s) => {
        this.stats = s;
        this.loading = false;
      },
      error: (err) => {
        this.error = extractError(err);
        this.loading = false;
      },
    });
  }
}