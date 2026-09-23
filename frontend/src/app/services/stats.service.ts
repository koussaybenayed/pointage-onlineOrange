import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { UserWeekStats } from '../models/models';

@Injectable({ providedIn: 'root' })
export class StatsService {
  private readonly API = '/api/stats';

  constructor(private http: HttpClient) {}

  getMyStats(start?: string): Observable<UserWeekStats> {
    let params = new HttpParams();
    if (start) params = params.set('start', start);
    return this.http.get<UserWeekStats>(`${this.API}/my`, { params });
  }
}
