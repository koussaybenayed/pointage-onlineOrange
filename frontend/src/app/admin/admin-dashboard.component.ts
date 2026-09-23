import { Component, OnInit } from '@angular/core';
import { CommonModule } from '@angular/common';
import { FormsModule } from '@angular/forms';
import { BaseChartDirective } from 'ng2-charts';
import { Chart, ChartData, ChartOptions, registerables } from 'chart.js';
import { AdminService } from '../services/admin.service';
import { OnlineDayAdmin, UserWeekStats, UserTicketStatsDto, TicketAlert, TicketEvolutionDto, B2B_MEMBERS, GP_MEMBERS, PlainteOverview, PlainteSlice, PlainteSearchResult } from '../models/models';
import { extractError } from '../utils/error.util';

Chart.register(...registerables);

function piePercentLabelPlugin(): any {
  return {
    id: 'innerPercent',
    afterDatasetsDraw(chart: any) {
      const meta = chart.getDatasetMeta(0);
      if (!meta || !meta.data) return;
      const type = chart.config.type;
      if (type !== 'pie' && type !== 'doughnut') return;
      const first = meta.data[0];
      if (!first || typeof first.startAngle !== 'number') return;
      const dataset = chart.data.datasets[0] as any;
      const values: any[] = dataset.data || [];
      const total = values.reduce((a, b) => a + (Number(b) || 0), 0);
      if (!total) return;
      const area = chart.chartArea;
      const cx = (area.left + area.right) / 2;
      const cy = (area.top + area.bottom) / 2;
      const ctx = chart.ctx;
      ctx.save();
      ctx.font = '600 14px "Segoe UI", system-ui, sans-serif';
      ctx.fillStyle = '#ffffff';
      ctx.textAlign = 'center';
      ctx.textBaseline = 'middle';
      ctx.shadowColor = 'rgba(0,0,0,0.35)';
      ctx.shadowBlur = 4;
      const rOuter = first.outerRadius;
      const rInner = first.innerRadius || 0;
      const midR = rInner + (rOuter - rInner) * 0.55;
      values.forEach((val, i) => {
        const arc = meta.data[i];
        if (!arc) return;
        const label = String(Number(val) || 0);
        const mid = (arc.startAngle + arc.endAngle) / 2;
        ctx.fillText(label, cx + Math.cos(mid) * midR, cy + Math.sin(mid) * midR);
      });
      ctx.restore();
    },
  };
}
function barValueLabelPlugin(): any {
  return {
    id: 'barValueLabel',
    afterDatasetsDraw(chart: any) {
      const meta = chart.getDatasetMeta(0);
      if (!meta || !meta.data) return;
      const type = chart.config.type;
      if (type !== 'bar') return;
      const dataset = chart.data.datasets[0] as any;
      const values: any[] = dataset.data || [];
      const area = chart.chartArea;
      const ctx = chart.ctx;
      const horizontal = (chart.config.options || {}).indexAxis === 'y';
      ctx.save();
      ctx.font = '600 13px "Segoe UI", system-ui, sans-serif';
      ctx.textBaseline = 'middle';
      ctx.shadowColor = 'rgba(255,255,255,0.9)';
      ctx.shadowBlur = 3;
      values.forEach((val, i) => {
        const bar = meta.data[i];
        if (!bar) return;
        const label = String(Number(val) || 0);
        ctx.fillStyle = '#1a1c21';
        if (horizontal) {
          ctx.textAlign = 'left';
          ctx.fillText(label, bar.x + 6, bar.y);
        } else {
          ctx.textAlign = 'center';
          ctx.font = '600 12px "Segoe UI", system-ui, sans-serif';
          ctx.fillText(label, bar.x, Math.max(area.top, bar.y) - 6);
        }
      });
      ctx.restore();
    },
  };
}

Chart.register(piePercentLabelPlugin(), barValueLabelPlugin());

interface UserDayGroup {
  userName: string;
  days: OnlineDayAdmin[];
}

interface PlainteDim {
  key: string;
  title: string;
  kind: 'pie' | 'bar';
}

const PLAINTE_PALETTE = ['#3b82f6', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#6366f1', '#14b8a6', '#a3e635', '#f43f5e', '#0ea5e9', '#a855f7', '#facc15', '#2dd4bf', '#fb923c', '#e879f9', '#94a3b8'];

@Component({
  selector: 'app-admin-dashboard',
  standalone: true,
  imports: [CommonModule, FormsModule, BaseChartDirective],
  templateUrl: './admin-dashboard.component.html',
  styleUrls: ['./admin-dashboard.component.scss'],
})
export class AdminDashboardComponent implements OnInit {
  stats: UserWeekStats[] = [];
  onlineDays: OnlineDayAdmin[] = [];
  dayGroups: UserDayGroup[] = [];
  activeTab: 'stats' | 'days' | 'tickets' | 'plaintes' = 'stats';
  loading = true;
  error = '';
  monthStart!: string;

  // Tickets
  ticketTeam: 'B2B' | 'GP' = 'B2B';
  ticketStats: UserTicketStatsDto[] = [];
  ticketAlerts: TicketAlert[] = [];
  ticketLoading = false;
  expandedUsers: Set<string> = new Set();
  sourceFilter: 'ALL' | 'SMC_BO' | 'ATP' | 'GP' = 'ALL';
  ticketToken = 0;
  evolutionToken = 0;

  // Evolution (curves)
  evolutionLoading = false;
  allTicketsChartData: ChartData<'line'> = { labels: [], datasets: [] };
  smcboChartData: ChartData<'line'> = { labels: [], datasets: [] };
  atpChartData: ChartData<'line'> = { labels: [], datasets: [] };
  allTicketsChartOptions: ChartOptions<'line'> = {};
  smcboChartOptions: ChartOptions<'line'> = {};
  atpChartOptions: ChartOptions<'line'> = {};
  masseChartData: ChartData<'line'> = { labels: [], datasets: [] };
  unitaireChartData: ChartData<'line'> = { labels: [], datasets: [] };
  sourceChartData: ChartData<'line'> = { labels: [], datasets: [] };
  acquittementChartData: ChartData<'line'> = { labels: [], datasets: [] };
  masseChartOptions: ChartOptions<'line'> = {};
  unitaireChartOptions: ChartOptions<'line'> = {};
  sourceChartOptions: ChartOptions<'line'> = {};
  acquittementChartOptions: ChartOptions<'line'> = {};
  typeProduitOptions: string[] = [];
  typeProduitFilter = 'ALL';
  private acquittementEv: TicketEvolutionDto | null = null;

  // Upload
  uploadOpen = false;
  selectedFiles: File[] = [];
  uploading = false;
  uploadMsg = '';
  uploadErr = '';

  // Plaintes (complaints)
readonly plainteDims: PlainteDim[] = [
    { key: 'PRIORITE', title: 'Priorité', kind: 'pie' },
    { key: 'TYPE_CLIENT', title: 'Répartition type client', kind: 'pie' },
    { key: 'RESPONSABILITE', title: 'Responsabilité (OTN / Client / Tiers)', kind: 'pie' },
    { key: 'REPARE_PAR', title: 'Réparé par', kind: 'pie' },
    { key: 'ETAT_TICKET', title: 'Etat ticket', kind: 'bar' },
    { key: 'TYPE_PRODUIT', title: 'Type de produit', kind: 'bar' },
    { key: 'PRODUIT_SERVICE', title: 'Type produit × Service impacté', kind: 'bar' },
  ];
  plainteOverview: PlainteOverview | null = null;
  plainteStats: Record<string, PlainteSlice[]> = {};
  plainteFilter = 'ALL';
  plainteCharts: Record<string, any> = {};
  plaintePieOptions: ChartOptions<'pie'> = {
    responsive: true,
    maintainAspectRatio: false,
    plugins: {
      legend: { position: 'bottom', labels: { color: '#111111', font: { size: 11 } } },
    },
  };
  plainteBarOptions: ChartOptions<'bar'> = {
    indexAxis: 'y',
    responsive: true,
    maintainAspectRatio: false,
    plugins: { legend: { display: false } },
    scales: {
      x: { beginAtZero: true, ticks: { color: '#94a3b8', precision: 0 }, grid: { color: 'rgba(148,163,184,0.1)' } },
      y: { ticks: { color: '#94a3b8', autoSkip: false }, grid: { display: false } },
    },
  };
plainteUploadOpen = false;
  plainteSelectedFiles: File[] = [];
  plainteUploading = false;
  plainteUploadMsg = '';
  plainteUploadErr = '';
  plainteLoading = false;
  private plainteToken = 0;
  plainteSearchQ = '';
  plainteSearchResults: PlainteSearchResult[] = [];
  plainteSearching = false;
  private plainteSearchToken = 0;
  exportingPptx = false;
  exportErr = '';

  // Admin reservations
  members = B2B_MEMBERS;
  selectedMemberId: number | null = null;
  bookingDate = '';
  bookingMsg = '';
  bookingErr = '';

  constructor(private adminService: AdminService) {}

  ngOnInit(): void {
    this.initMonth();
    this.loadAll();
    this.loadEvolution();
  }

  get monthLabel(): string {
    return new Date(this.monthStart + 'T00:00:00').toLocaleDateString('fr-FR', { month: 'long', year: 'numeric' });
  }

  get monthEnd(): string {
    const d = new Date(this.monthStart + 'T00:00:00');
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${new Date(d.getFullYear(), d.getMonth() + 1, 0).getDate()}`;
  }

  setTab(tab: 'stats' | 'days' | 'tickets' | 'plaintes'): void {
    this.activeTab = tab;
    if (tab === 'tickets' && this.ticketStats.length === 0 && this.ticketAlerts.length === 0) {
      this.loadTickets();
    }
    if (tab === 'plaintes' && this.plainteOverview === null) {
      this.loadPlaintes();
    }
    if (tab === 'days') {
      this.loadDays();
    }
  }

  setTicketTeam(team: 'B2B' | 'GP'): void {
    this.ticketTeam = team;
    this.members = team === 'B2B' ? B2B_MEMBERS : GP_MEMBERS;
    this.loadTickets();
    this.loadEvolution();
  }

  setSourceFilter(f: 'ALL' | 'SMC_BO' | 'ATP' | 'GP'): void {
    this.sourceFilter = f;
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

  visibleTickets(stats: UserTicketStatsDto): TicketDtoView[] {
    const all = stats.tickets;
    if (this.sourceFilter === 'ALL') return all.map(t => ({ ...t, label: this.sourceLabel(t.source) }));
    return all.filter(t => this.sourceMatches(t.source)).map(t => ({ ...t, label: this.sourceLabel(t.source) }));
  }

  filteredTicketStats(): UserTicketStatsDto[] {
    if (this.sourceFilter === 'ALL') return this.ticketStats;
    return this.ticketStats
      .map(u => ({ ...u, tickets: u.tickets.filter(t => this.sourceMatches(t.source)) }))
      .filter(u => u.tickets.length > 0);
  }

  private sourceMatches(source: string): boolean {
    const s = source || '';
    if (this.sourceFilter === 'SMC_BO') return s.includes('SMC') && !s.includes('TDB');
    if (this.sourceFilter === 'ATP') return s.includes('ATP');
    if (this.sourceFilter === 'GP') return s.includes('TDB');
    return true;
  }

  private sourceLabel(source: string): string {
    if (!source) return 'Autre';
    if (source.includes('TDB')) return 'TDB_SMC_BO (GP)';
    if (source.includes('SMC')) return 'SMC_BO';
    if (source.includes('ATP')) return 'ATP';
    return source;
  }

  previousMonth(): void {
    const d = new Date(this.monthStart + 'T00:00:00');
    this.monthStart = this.iso(new Date(d.getFullYear(), d.getMonth() - 1, 1));
    this.loadAll();
    if (this.activeTab === 'tickets') this.loadTickets();
    this.loadEvolution();
  }

  nextMonth(): void {
    const d = new Date(this.monthStart + 'T00:00:00');
    this.monthStart = this.iso(new Date(d.getFullYear(), d.getMonth() + 1, 1));
    this.loadAll();
    if (this.activeTab === 'tickets') this.loadTickets();
    this.loadEvolution();
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

  // ----- Admin reservations -----

  onMemberSelect(): void {
    this.bookingMsg = '';
    this.bookingErr = '';
  }

  get todayIso(): string {
    const d = new Date();
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }

  isFriday(dateStr: string): boolean {
    return new Date(dateStr + 'T00:00:00').getDay() === 5;
  }

  isWeekend(dateStr: string): boolean {
    const d = new Date(dateStr + 'T00:00:00').getDay();
    return d === 0 || d === 6;
  }

  adminBook(): void {
    this.bookingMsg = '';
    this.bookingErr = '';
    if (!this.selectedMemberId) {
      this.bookingErr = 'Choisissez un membre.';
      return;
    }
    if (!this.bookingDate) {
      this.bookingErr = 'Choisissez une date.';
      return;
    }
    if (this.isFriday(this.bookingDate) || this.isWeekend(this.bookingDate)) {
      this.bookingErr = 'Vendredi et week-end non autorisés.';
      return;
    }
    const member = this.members[this.selectedMemberId - 1];
    this.adminService.adminBookDay(this.selectedMemberId, this.bookingDate).subscribe({
      next: () => {
        this.bookingMsg = `Jour du ${this.formatDate(this.bookingDate)} réservé pour ${member.fullName}.`;
        this.bookingDate = '';
        this.loadDays();
        this.loadAll();
      },
      error: (err) => {
        this.bookingErr = extractError(err);
      },
    });
  }

  adminCancel(od: OnlineDayAdmin): void {
    this.bookingErr = '';
    this.bookingMsg = '';
    const id = this.members.findIndex(x => x.fullName.toLowerCase() === od.userName.toLowerCase()) + 1;
    this.adminService.adminCancelDay(id, od.id).subscribe({
      next: () => {
        this.loadDays();
        this.loadAll();
      },
      error: (err) => {
        this.bookingErr = extractError(err);
      },
    });
  }

  // ----- Upload -----

  onFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.selectedFiles = Array.from(input.files);
      this.uploadMsg = '';
      this.uploadErr = '';
    }
  }

  uploadFile(): void {
    if (!this.selectedFiles.length) {
      this.uploadErr = 'Veuillez sélectionner au moins un fichier .xlsx.';
      return;
    }
    this.uploading = true;
    this.uploadMsg = '';
    this.uploadErr = '';
    const reportType = this.ticketTeam === 'GP' ? 'TDB_SMC_BO' : undefined;
    this.adminService.uploadExcel(this.selectedFiles, reportType).subscribe({
      next: (res) => {
        this.uploading = false;
        this.uploadMsg = 'Fichier(s) ' + res.file + ' téléversé(s) avec succès.';
        this.selectedFiles = [];
        this.uploadOpen = false;
        this.loadTickets();
        this.loadEvolution();
        this.loadAll();
      },
      error: (err) => {
        this.uploading = false;
        this.uploadErr = extractError(err);
      },
    });
  }

  // ----- Plaintes -----

  setPlaintesFilter(filter: string): void {
    this.plainteFilter = filter;
    this.loadPlaintes();
  }

  onPlaintesFileSelected(event: Event): void {
    const input = event.target as HTMLInputElement;
    if (input.files && input.files.length > 0) {
      this.plainteSelectedFiles = Array.from(input.files);
      this.plainteUploadMsg = '';
      this.plainteUploadErr = '';
    }
  }

  uploadPlaintesFiles(): void {
    if (!this.plainteSelectedFiles.length) {
      this.plainteUploadErr = 'Veuillez sélectionner au moins un fichier .xlsx.';
      return;
    }
    this.plainteUploading = true;
    this.plainteUploadMsg = '';
    this.plainteUploadErr = '';
    this.adminService.uploadPlaintes(this.plainteSelectedFiles).subscribe({
      next: (res) => {
        this.plainteUploading = false;
        this.plainteUploadMsg = 'Fichier(s) ' + res.file + ' téléversé(s) avec succès.';
        this.plainteSelectedFiles = [];
        this.plainteUploadOpen = false;
        this.plainteFilter = 'ALL';
        this.loadPlaintes();
      },
      error: (err) => {
        this.plainteUploading = false;
        this.plainteUploadErr = extractError(err);
      },
    });
  }

private loadPlaintes(): void {
    this.plainteLoading = true;
    const token = ++this.plainteToken;
    this.adminService.getPlaintes(this.plainteFilter === 'ALL' ? undefined : this.plainteFilter).subscribe({
      next: (data) => {
        if (token !== this.plainteToken) return;
        this.plainteOverview = data;
        this.plainteStats = data.stats;
        this.buildPlaintesCharts();
        this.plainteLoading = false;
      },
      error: () => {
        if (token !== this.plainteToken) return;
        this.plainteLoading = false;
      },
    });
  }

  searchPlaintes(): void {
    this.plainteSearching = true;
    this.plainteSearchResults = [];
    const token = ++this.plainteSearchToken;
    const q = this.plainteSearchQ.trim();
    if (!q) {
      this.plainteSearching = false;
      return;
    }
    this.adminService.searchPlaintes(q, this.plainteFilter === 'ALL' ? undefined : this.plainteFilter).subscribe({
      next: (results) => {
        if (token !== this.plainteSearchToken) return;
        this.plainteSearchResults = results;
        this.plainteSearching = false;
      },
      error: () => {
        if (token !== this.plainteSearchToken) return;
        this.plainteSearching = false;
      },
    });
  }

  clearPlaintesSearch(): void {
    this.plainteSearchQ = '';
    this.plainteSearchResults = [];
  }

  quitPlaintesSearch(): void {
    this.clearPlaintesSearch();
  }

  plainteSlices(key: string): PlainteSlice[] {
    return this.plainteStats[key] ?? [];
  }

  plainteTotal(key: string): number {
    return this.plainteSlices(key).reduce((a, s) => a + s.count, 0);
  }

  plaintePct(key: string, count: number): string {
    const total = this.plainteTotal(key);
    if (!total) return '0%';
    return ((count / total) * 100).toFixed(1) + '%';
  }

  plainteColor(key: string, index: number): string {
    void key;
    return PLAINTE_PALETTE[index % PLAINTE_PALETTE.length];
  }

private buildPlaintesCharts(): void {
    const charts: Record<string, ChartData<'pie'> | ChartData<'bar'>> = {};
    for (const dim of this.plainteDims) {
      const slices = this.plainteSlices(dim.key).slice();
      const cap = slices.length > 10 ? 10 : slices.length;
      let shown = slices;
      let others = 0;
      if (slices.length > cap) {
        shown = slices.slice(0, cap);
        others = slices.reduce((a, s) => a + s.count, 0) - shown.reduce((a, s) => a + s.count, 0);
      }
      const labels = shown.map(s => s.label.length > 28 ? s.label.slice(0, 27) + '…' : s.label);
      const data = shown.map(s => s.count);
      const colors = shown.map((_, i) => PLAINTE_PALETTE[i % PLAINTE_PALETTE.length]);
      if (others > 0) {
        labels.push('Autres');
        data.push(others);
        colors.push('#64748b');
      }
      if (dim.kind === 'bar') {
        charts[dim.key] = {
          labels,
          datasets: [
            {
              data,
              backgroundColor: colors,
              borderRadius: 6,
              barThickness: 24,
            },
          ],
        };
      } else {
        charts[dim.key] = {
          labels,
          datasets: [{ data, backgroundColor: colors }],
        };
      }
    }
    this.plainteCharts = charts;
  }

  // ----- Loading -----

  private loadAll(): void {
    this.loading = true;
    this.error = '';
    this.adminService.getAllStats(this.monthStart).subscribe({
      next: (s) => {
        this.stats = s;
        this.adminService.getAllOnlineDays(this.monthStart).subscribe({
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
  }

  private loadDays(): void {
    this.adminService.getAllOnlineDays(this.monthStart).subscribe({
      next: (d) => {
        this.onlineDays = d;
        this.buildDayGroups();
      },
      error: () => {},
    });
  }

  private loadTickets(): void {
    this.ticketLoading = true;
    this.ticketStats = [];
    this.ticketAlerts = [];
    this.expandedUsers.clear();
    const token = ++this.ticketToken;
    const team = this.ticketTeam;
    this.adminService.getTicketReports(team, this.monthStart, this.monthEnd).subscribe({
      next: (data) => {
        if (token !== this.ticketToken) return;
        this.ticketStats = data.userStats;
        this.ticketAlerts = data.alerts;
        this.ticketLoading = false;
      },
      error: () => {
        if (token !== this.ticketToken) return;
        this.ticketLoading = false;
      },
    });
  }

  private loadEvolution(): void {
    this.evolutionLoading = true;
    const token = ++this.evolutionToken;
    const team = this.ticketTeam;
    this.typeProduitFilter = 'ALL';
    this.adminService.getTicketEvolution(team, this.monthStart, this.monthEnd).subscribe({
      next: (ev) => {
        if (token !== this.evolutionToken) return;
        this.buildCharts(ev);
        this.evolutionLoading = false;
      },
      error: () => {
        if (token !== this.evolutionToken) return;
        this.evolutionLoading = false;
      },
    });
    this.adminService.getTicketAcquittementEvolution(team, this.monthStart, this.monthEnd).subscribe({
      next: (ev) => {
        if (token !== this.evolutionToken) return;
        this.buildAcquittementChart(ev);
      },
      error: () => {},
    });
    this.adminService.getTicketCreatedEvolution(team, this.monthStart, this.monthEnd).subscribe({
      next: (ev) => {
        if (token !== this.evolutionToken) return;
        this.buildCreatedCharts(ev);
      },
      error: () => {},
    });
  }

  private buildCharts(ev: TicketEvolutionDto): void {
    this.masseChartData = {
      labels: ev.dates,
      datasets: [
        {
          label: 'Tickets résolus',
          data: ev.totalPerDay,
          borderColor: '#3b82f6',
          backgroundColor: 'rgba(59,130,246,0.1)',
          fill: true,
          tension: 0.35,
          pointRadius: 4,
        },
      ],
    };

    const palette = ['#3b82f6', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#6366f1'];

    this.unitaireChartData = {
      labels: ev.dates,
      datasets: Object.keys(ev.perUser).map((u, i) => ({
        label: u,
        data: ev.perUser[u],
        borderColor: palette[i % palette.length],
        backgroundColor: 'rgba(0,0,0,0)',
        fill: false,
        tension: 0.35,
        pointRadius: 3,
      })),
    };

    this.sourceChartData = {
      labels: ev.dates,
      datasets: Object.keys(ev.bySource).map((s) => ({
        label: s === 'SMC_BO' ? 'Tickets SMC_BO' : (s === 'ATP' ? 'Workflow ATP' : (s === 'GP' ? 'GP (TDB_SMC_BO)' : s)),
        data: ev.bySource[s],
        borderColor: s === 'SMC_BO' ? '#10b981' : (s === 'ATP' ? '#8b5cf6' : (s === 'GP' ? '#f59e0b' : '#94a3b8')),
        backgroundColor: 'rgba(0,0,0,0)',
        fill: false,
        tension: 0.35,
        pointRadius: 3,
      })),
    };

    const baseOpts: ChartOptions<'line'> = {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { labels: { color: '#cbd5e1', font: { size: 11 } } },
      },
      scales: {
        x: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(148,163,184,0.1)' } },
        y: { beginAtZero: true, ticks: { color: '#94a3b8', precision: 0 }, grid: { color: 'rgba(148,163,184,0.1)' } },
      },
    };
    this.masseChartOptions = baseOpts;
    this.unitaireChartOptions = baseOpts;
    this.sourceChartOptions = baseOpts;
  }

  private buildAcquittementChart(ev: TicketEvolutionDto): void {
    this.acquittementEv = ev;
    this.typeProduitOptions = Object.keys(ev.perUserBySource ?? {}).sort();
    if (this.typeProduitFilter !== 'ALL' && !this.typeProduitOptions.includes(this.typeProduitFilter)) {
      this.typeProduitFilter = 'ALL';
    }
    this.renderAcquittementChart();
  }

  setTypeProduitFilter(type: string): void {
    this.typeProduitFilter = type;
    this.renderAcquittementChart();
  }

  private renderAcquittementChart(): void {
    const ev = this.acquittementEv;
    if (!ev) return;
    const palette = ['#3b82f6', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#6366f1'];

    const selected = this.typeProduitFilter !== 'ALL' ? this.typeProduitFilter : null;
    const totalData = selected ? ev.bySource[selected] ?? [] : ev.totalPerDay;
    const perUser = selected ? ev.perUserBySource?.[selected] ?? {} : ev.perUser;

    this.acquittementChartData = {
      labels: ev.dates,
      datasets: [
        {
          label: selected ? `Total ${selected}` : 'Total',
          data: totalData,
          borderColor: '#3b82f6',
          backgroundColor: 'rgba(59,130,246,0.1)',
          fill: true,
          tension: 0.35,
          pointRadius: 4,
        },
        ...this.perUserLines(perUser, palette),
      ],
    };
    const baseOpts: ChartOptions<'line'> = {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { labels: { color: '#cbd5e1', font: { size: 11 } } },
      },
      scales: {
        x: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(148,163,184,0.1)' } },
        y: { beginAtZero: true, ticks: { color: '#94a3b8', precision: 0 }, grid: { color: 'rgba(148,163,184,0.1)' } },
      },
    };
    this.acquittementChartOptions = baseOpts;
  }

  private buildCreatedCharts(ev: TicketEvolutionDto): void {
    const palette = ['#3b82f6', '#f59e0b', '#10b981', '#ef4444', '#8b5cf6', '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#6366f1'];

    const baseOpts: ChartOptions<'line'> = {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { labels: { color: '#cbd5e1', font: { size: 11 } } },
      },
      scales: {
        x: { ticks: { color: '#94a3b8' }, grid: { color: 'rgba(148,163,184,0.1)' } },
        y: { beginAtZero: true, ticks: { color: '#94a3b8', precision: 0 }, grid: { color: 'rgba(148,163,184,0.1)' } },
      },
    };
    this.allTicketsChartOptions = baseOpts;
    this.smcboChartOptions = baseOpts;
    this.atpChartOptions = baseOpts;

    this.allTicketsChartData = {
      labels: ev.dates,
      datasets: [
        {
          label: 'Tous les tickets',
          data: ev.totalPerDay,
          borderColor: '#3b82f6',
          backgroundColor: 'rgba(59,130,246,0.1)',
          fill: true,
          tension: 0.35,
          pointRadius: 4,
        },
        ...Object.keys(ev.perUser).map((u, i) => ({
          label: u,
          data: ev.perUser[u],
          borderColor: palette[i % palette.length],
          backgroundColor: 'rgba(0,0,0,0)',
          fill: false,
          tension: 0.35,
          pointRadius: 3,
        })),
      ],
    };

    this.smcboChartData = {
      labels: ev.dates,
      datasets: [
        {
          label: 'Total SMC_BO',
          data: ev.bySource['SMC_BO'] ?? [],
          borderColor: '#10b981',
          backgroundColor: 'rgba(16,185,129,0.1)',
          fill: true,
          tension: 0.35,
          pointRadius: 4,
        },
        ...this.perUserLines(ev.perUserBySource?.['SMC_BO'] ?? {}, palette),
      ],
    };

    this.atpChartData = {
      labels: ev.dates,
      datasets: [
        {
          label: 'Total ATP_WO',
          data: ev.bySource['ATP'] ?? [],
          borderColor: '#8b5cf6',
          backgroundColor: 'rgba(139,92,246,0.1)',
          fill: true,
          tension: 0.35,
          pointRadius: 4,
        },
        ...this.perUserLines(ev.perUserBySource?.['ATP'] ?? {}, palette),
      ],
    };
  }

  private perUserLines(perUser: Record<string, number[]>, palette: string[]): any[] {
    return Object.keys(perUser).map((u, i) => ({
      label: u,
      data: perUser[u],
      borderColor: palette[i % palette.length],
      backgroundColor: 'rgba(0,0,0,0)',
      fill: false,
      tension: 0.35,
      pointRadius: 3,
    }));
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

  async exportDashboardPptx(): Promise<void> {
    this.exportErr = '';
    interface ChartDef {
      title: string;
      type: 'line' | 'pie' | 'bar';
      data: any;
      options: any;
    }
    const defs: ChartDef[] = [];
    if (this.allTicketsChartData.datasets.length) {
      defs.push({ title: `Tous les tickets — créés par jour et par membre (B2B)`, type: 'line', data: this.allTicketsChartData, options: this.allTicketsChartOptions });
    }
    if (this.smcboChartData.datasets.length) {
      defs.push({ title: `Tickets SMC_BO — créés par jour et par membre (B2B)`, type: 'line', data: this.smcboChartData, options: this.smcboChartOptions });
    }
    if (this.atpChartData.datasets.length) {
      defs.push({ title: `Tickets ATP_WO — créés par jour et par membre (B2B)`, type: 'line', data: this.atpChartData, options: this.atpChartOptions });
    }
    if (this.masseChartData.datasets.length) {
      defs.push({ title: `Rendement en masse — tickets résolus par jour (GP)`, type: 'line', data: this.masseChartData, options: this.masseChartOptions });
    }
    if (this.unitaireChartData.datasets.length) {
      defs.push({ title: `Rendement unitaire — tickets par jour et par membre`, type: 'line', data: this.unitaireChartData, options: this.unitaireChartOptions });
    }
    if (this.sourceChartData.datasets.length) {
      defs.push({ title: `Tickets SMC_BO vs Workflow ATP`, type: 'line', data: this.sourceChartData, options: this.sourceChartOptions });
    }
    if (this.acquittementChartData.datasets.length) {
      const tp = this.typeProduitFilter !== 'ALL' ? ` (${this.typeProduitFilter})` : '';
      defs.push({ title: `Rendement acquittement — tickets par jour et par utilisateur acq.${tp} (GP)`, type: 'line', data: this.acquittementChartData, options: this.acquittementChartOptions });
    }
    for (const dim of this.plainteDims) {
      if (this.plainteCharts[dim.key]) {
        defs.push({
          title: `Plaintes — ${dim.title}${this.plainteFilter !== 'ALL' ? ` (${this.plainteFilter})` : ' (tous les fichiers)'}`,
          type: dim.kind,
          data: this.plainteCharts[dim.key],
          options: dim.kind === 'bar' ? this.plainteBarOptions : this.plaintePieOptions,
        });
      }
    }
    if (!defs.length) {
      this.exportErr = 'Aucun graphique à exporter (charger d’abord les données).';
      return;
    }
    this.exportingPptx = true;
    try {
      const { default: PptxGenJS } = await import('pptxgenjs');
      const pptx = new PptxGenJS();
      pptx.layout = 'LAYOUT_16x9';
      const titleSlide = pptx.addSlide();
      titleSlide.background = { color: 'FFFFFF' };
      titleSlide.addText(`Tableau de bord — ${this.monthLabel}`, { x: 0.8, y: 2.2, w: 8.4, h: 1.2, fontSize: 34, bold: true, color: '111111', align: 'center', fontFace: 'Segoe UI' });
      titleSlide.addText(`${defs.length} graphiques`, { x: 0.8, y: 3.4, w: 8.4, h: 0.6, fontSize: 16, color: '6B7280', align: 'center', fontFace: 'Segoe UI' });
      for (const def of defs) {
        const dataUrl = await this.renderChartPng(def.type, def.data, def.options);
        if (!dataUrl) continue;
        const slide = pptx.addSlide();
        slide.background = { color: 'FFFFFF' };
        slide.addText(def.title, { x: 0.5, y: 0.25, w: 9, h: 0.8, fontSize: 16, bold: true, color: '111111', fontFace: 'Segoe UI' });
        slide.addImage({ data: dataUrl, x: 0.6, y: 1.1, w: 8.8, h: 4.4, sizing: { type: 'contain', w: 8.8, h: 4.4 } });
      }
      await pptx.writeFile({ fileName: `Tableau_de_bord_${this.monthLabel.replace(/\s+/g, '_')}.pptx` });
    } catch (e) {
      this.exportErr = extractError(e);
    } finally {
      this.exportingPptx = false;
    }
  }

  private renderChartPng(type: 'line' | 'pie' | 'bar', data: any, options: any): Promise<string | null> {
    return new Promise(resolve => {
      const width = 1280;
      const height = 700;
      const holder = document.createElement('div');
      holder.style.position = 'fixed';
      holder.style.left = '-9999px';
      holder.style.top = '0';
      holder.style.width = `${width}px`;
      holder.style.height = `${height}px`;
      const canvas = document.createElement('canvas');
      canvas.width = width;
      canvas.height = height;
      holder.appendChild(canvas);
      document.body.appendChild(holder);
      const opts = { ...options, responsive: true, maintainAspectRatio: false, animation: false };
      let chart: Chart | null = null;
      try {
        chart = new Chart(canvas.getContext('2d')!, { type, data, options: opts } as any);
        window.setTimeout(() => {
          try {
            const url = canvas.toDataURL('image/png');
            resolve(url);
          } catch (e) {
            resolve(null);
          } finally {
            chart?.destroy();
            holder.remove();
          }
        }, 250);
      } catch (e) {
        chart?.destroy();
        holder.remove();
        resolve(null);
      }
    });
  }

  private initMonth(): void {
    const now = new Date();
    this.monthStart = this.iso(new Date(now.getFullYear(), now.getMonth(), 1));
  }

  private iso(d: Date): string {
    return `${d.getFullYear()}-${String(d.getMonth() + 1).padStart(2, '0')}-${String(d.getDate()).padStart(2, '0')}`;
  }
}

interface TicketDtoView {
  ticketNumber: string;
  userName: string;
  createdDate: string | null;
  resolutionDate: string;
  createdDateTime: string | null;
  resolutionDateTime: string;
  followUpDate: string | null;
  source: string;
  label: string;
}

