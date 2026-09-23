import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { Report } from '../models/models';

@Injectable({ providedIn: 'root' })
export class ReportService {
  private readonly API = '/api/reports';

  constructor(private http: HttpClient) {}

  submit(onlineDayId: number, content: string): Observable<Report> {
    return this.http.post<Report>(this.API, { onlineDayId, content });
  }

  getMy(start?: string): Observable<Report[]> {
    let params = new HttpParams();
    if (start) params = params.set('start', start);
    return this.http.get<Report[]>(`${this.API}/my`, { params });
  }
}
