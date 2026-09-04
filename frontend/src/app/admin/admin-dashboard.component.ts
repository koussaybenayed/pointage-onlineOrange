import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { AdminService } from '../services/admin.service';
import { OnlineDayAdmin, Report, UserWeekStats, UserTicketStatsDto, TicketAlert } from '../models/models';
import { extractError } from '../utils/error.util';

interface UserDayGroup {
  userName: string;
  days: OnlineDayAdmin[];
}

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.scss'],
})
export class AdminDashboardComponent implements OnInit {
  stats: UserWeekStats[] = [];
  reports: Report[] = [];
  onlineDays: OnlineDayAdmin[] = [];
  dayGroups: UserDayGroup[] = [];
  activeTab: 'stats' | 'reports' | 'days' | 'tickets' = 'stats';
  loading = true;
  error = '';
  weekStart!: string;

  ticketTeam: 'B2B' | 'GP' = 'B2B';
  ticketStats: UserTicketStatsDto[] = [];
  ticketAlerts: TicketAlert[] = [];
  ticketLoading = false;
  expandedUsers: Set<string> = new Set();

  uploadOpen = false;
  uploadReportType: 'SMC_BO' | 'ATP_WO' | 'TDB_SMC_BO' = 'SMC_BO';
  selectedFile: File | null = null;
  uploading = false;
  uploadMsg = '';
  uploadErr = '';

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.initWeek();
    this.loadAll();
  }

  get weekLabel(): string {
    return new Date(this.weekStart + 'T00:00:00').toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'long', year: 'numeric' });
  }

  get weekEnd(): string {
    const d = new Date(this.weekStart + 'T00:00:00');
    d.setDate(d.getDate() + 6);
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  setTab(tab: 'stats' | 'reports' | 'days' | 'tickets'): void {
    this.activeTab = tab;
    if (tab === 'tickets' && this.ticketStats.length === 0 && this.ticketAlerts.length === 0) {
      this.loadTickets();
    }
  }

  setTicketTeam(team: 'B2B' | 'GP'): void {
    this.ticketTeam = team;
    this.loadTickets();
  }

  toggleUser(userName: string): void {
    if (this.expandedUsers.has(userName)) {
      this.expandedUsers.delete(userName);
    } else {
      this.expandedUsers.add(userName);
    }
  }

  isUserExpanded(userName: string): boolean {
    return this.expandedUsers.has(userName);
  }

  previousWeek(): void {
    const d = new Date(this.weekStart + 'T00:00:00');
    d.setDate(d.getDate() - 7);
    this.weekStart = this.iso(d);
    this.loadAll();
    if (this.activeTab === 'tickets') this.loadTickets();
  }

  nextWeek(): void {
    const d = new Date(this.weekStart + 'T00:00:00');
    d.setDate(d.getDate() + 7);
    this.weekStart = this.iso(d);
    this.loadAll();
    if (this.activeTab === 'tickets') this.loadTickets();
  }

  formatDay(dateStr: string): string {
    return new Date(dateStr + 'T00:00:00').toLocaleDateString('fr-FR', { weekday: 'long', day: 'numeric', month: 'short' });
  }

  formatDate(dateStr: string): string {
    return new Date(dateStr + 'T00:00:00').toLocaleDateString('fr-FR', { day: 'numeric', month: 'short', year: 'numeric' });
  }

  formatDateTime(dtStr: string): string {
    const d = new Date(dtStr);
    return d.toLocaleDateString('fr-FR', { day: 'numeric', month: 'short' }) + ' ' + d.toLocaleTimeString('fr-FR', { hour: '2-digit', minute: '2-digit' });
  }

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFile = input.files[0];
      this.uploadMsg = '';
      this.uploadErr = '';
    }
  }

  get reportTypeLabel(): string {
    switch (this.uploadReportType) {
      case 'SMC_BO': return 'Rapport_SMC_BO';
      case 'ATP_WO': return 'Rapport ATP_WO';
      case 'TDB_SMC_BO': return 'TDBRapport_SMC_BO';
    }
  }

  uploadFile(): void {
    if (!this.selectedFile) {
      this.uploadErr = 'Veuillez sélectionner un fichier .xlsx.';
      return;
    }
    this.uploading = true;
    this.uploadMsg = '';
    this.uploadErr = '';
    this.adminService.uploadExcel(this.selectedFile, this.ticketTeam, this.uploadReportType).subscribe({
      next: (res) => {
        this.uploading = false;
        this.uploadMsg = 'Fichier ' + res.file + ' téléversé avec succès.';
        this.selectedFile = null;
        this.uploadOpen = false;
        this.loadTickets();
      },
      error: (err) => {
        this.uploading = false;
        this.uploadErr = extractError(err);
      },
    });
  }

  private loadAll(): void {
    this.loading = true;
    this.error = '';
    this.adminService.getAllStats(this.weekStart).subscribe({
      next: (s) => {
        this.stats = s;
        this.adminService.getAllReports(this.weekStart).subscribe({
          next: (r) => {
            this.reports = r;
            this.adminService.getAllOnlineDays(this.weekStart).subscribe({
              next: (d) => {
                this.onlineDays = d;
                this.buildDayGroups();
                this.loading = false;
              },
              error: (err) => {
                this.error = extractError(err);
                this.loading = false;
              },
            });
          },
          error: (err) => {
            this.error = extractError(err);
            this.loading = false;
          },
        });
      },
      error: (err) => {
        this.error = extractError(err);
        this.loading = false;
      },
    });
  }

  private loadTickets(): void {
    this.ticketLoading = true;
    this.ticketStats = [];
    this.ticketAlerts = [];
    this.expandedUsers.clear();
    this.adminService.getTicketReports(this.ticketTeam, this.weekStart, this.weekEnd).subscribe({
      next: (data) => {
        this.ticketStats = data.userStats;
        this.ticketAlerts = data.alerts;
        this.ticketLoading = false;
      },
      error: () => {
        this.ticketLoading = false;
      },
    });
  }

  private buildDayGroups(): void {
    const map = new Map<string, OnlineDayAdmin[]>();
    for (const d of this.onlineDays) {
      if (!map.has(d.userName)) map.set(d.userName, []);
      map.get(d.userName)!.push(d);
    }
    this.dayGroups = Array.from(map.entries())
      .map(([userName, days]) => ({ userName, days }))
      .sort((a, b) => a.userName.localeCompare(b.userName));
  }

  private initWeek(): void {
    const now = new Date();
    const day = now.getDay();
    const diff = day === 0 ? -6 : 1 - day;
    const d = new Date(now);
    d.setDate(now.getDate() + diff);
    this.weekStart = this.iso(d);
  }

  private iso(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}