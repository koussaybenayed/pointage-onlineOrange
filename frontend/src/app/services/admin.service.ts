import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { OnlineDayAdmin, Report, UserWeekStats, TicketReportResponse } from '../models/models';

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

  getTicketReports(team: string, start: string, end: string): Observable<TicketReportResponse> {
    let params = new HttpParams()
      .set('team', team)
      .set('start', start)
      .set('end', end);
    return this.http.get<TicketReportResponse>(`${this.API}/ticket-reports`, { params });
  }

  uploadExcel(file: File, team: string, reportType: string): Observable<{ status: string; file: string }> {
    const fd = new FormData();
    fd.append('file', file);
    fd.append('team', team);
    fd.append('reportType', reportType);
    return this.http.post<{ status: string; file: string }>(`${this.API}/upload-excel`, fd);
  }
}
