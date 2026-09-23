import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { OnlineDayAdmin, Report, UserWeekStats, TicketReportResponse, TicketEvolutionDto, OnlineDay, PlainteOverview, PlainteSearchResult } from '../models/models';

@Injectable({ providedIn: 'root' })
export class AdminService {
  private readonly API = 'http://localhost:8085/api/admin';

  constructor(private http: HttpClient) {}

  getAllStats(start?: string): Observable<UserWeekStats[]> {
    let params = new HttpParams();
    if (start) params = params.set('start', start);
    return this.http.get<UserWeekStats[]>(`${this.API}/stats`, { params });
  }

  getAllReports(start?: string): Observable<Report[]> {
    let params = new HttpParams();
    if (start) params = params.set('start', start);
    return this.http.get<Report[]>(`${this.API}/reports`, { params });
  }

  getAllOnlineDays(start?: string): Observable<OnlineDayAdmin[]> {
    let params = new HttpParams();
    if (start) params = params.set('start', start);
    return this.http.get<OnlineDayAdmin[]>(`${this.API}/online-days`, { params });
  }

  adminBookDay(userId: number, dayDate: string): Observable<OnlineDay> {
    return this.http.post<OnlineDay>(`${this.API}/online-days?userId=${userId}`, { dayDate });
  }

  adminCancelDay(userId: number, id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/online-days/${id}?userId=${userId}`);
  }

  getTicketReports(team: string, start: string, end: string): Observable<TicketReportResponse> {
    let params = new HttpParams()
      .set('team', team)
      .set('start', start)
      .set('end', end);
    return this.http.get<TicketReportResponse>(`${this.API}/ticket-reports`, { params });
  }

  getTicketEvolution(team: string, start: string, end: string): Observable<TicketEvolutionDto> {
    let params = new HttpParams()
      .set('team', team)
      .set('start', start)
      .set('end', end);
    return this.http.get<TicketEvolutionDto>(`${this.API}/ticket-evolution`, { params });
  }

  getTicketAcquittementEvolution(team: string, start: string, end: string): Observable<TicketEvolutionDto> {
    let params = new HttpParams()
      .set('team', team)
      .set('start', start)
      .set('end', end);
    return this.http.get<TicketEvolutionDto>(`${this.API}/ticket-acquittement-evolution`, { params });
  }

  getTicketCreatedEvolution(team: string, start: string, end: string): Observable<TicketEvolutionDto> {
    let params = new HttpParams()
      .set('team', team)
      .set('start', start)
      .set('end', end);
    return this.http.get<TicketEvolutionDto>(`${this.API}/ticket-created-evolution`, { params });
  }

  uploadExcel(files: File[], reportType?: string): Observable<{ status: string; file: string }> {
    const fd = new FormData();
    for (const f of files) {
      fd.append('files', f, f.name);
    }
    if (reportType) fd.append('reportType', reportType);
    return this.http.post<{ status: string; file: string }>(`${this.API}/upload-excel`, fd);
  }

  getPlaintes(file?: string): Observable<PlainteOverview> {
    let params = new HttpParams();
    if (file) params = params.set('file', file);
    return this.http.get<PlainteOverview>(`${this.API}/plaintes`, { params });
  }

  searchPlaintes(q: string, file?: string): Observable<PlainteSearchResult[]> {
    let params = new HttpParams();
    if (q) params = params.set('q', q);
    if (file) params = params.set('file', file);
    return this.http.get<PlainteSearchResult[]>(`${this.API}/plaintes/search`, { params });
  }

  uploadPlaintes(files: File[]): Observable<{ status: string; file: string }> {
    const fd = new FormData();
    for (const f of files) {
      fd.append('files', f, f.name);
    }
    return this.http.post<{ status: string; file: string }>(`${this.API}/upload-plaintes`, fd);
  }
}
