import { Injectable } from '@angular/core';
import { HttpClient, HttpParams } from '@angular/common/http';
import { Observable } from 'rxjs';
import { OnlineDay } from '../models/models';

@Injectable({ providedIn: 'root' })
export class OnlineDayService {
  private readonly API = 'http://localhost:8085/api/online-days';

  constructor(private http: HttpClient) {}

  getMyWeek(start?: string): Observable<OnlineDay[]> {
    let params = new HttpParams();
    if (start) params = params.set('start', start);
    return this.http.get<OnlineDay[]>(this.API, { params });
  }

  bookDay(dayDate: string): Observable<OnlineDay> {
    return this.http.post<OnlineDay>(this.API, { dayDate });
  }

  cancelDay(id: number): Observable<void> {
    return this.http.delete<void>(`${this.API}/${id}`);
  }
}
