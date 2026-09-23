import { Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { BehaviorSubject, Observable } from 'rxjs';
import { tap } from 'rxjs/operators';
import { AuthResponse } from '../models/models';

const TOKEN_KEY = 'access_token';
const REFRESH_KEY = 'refresh_token';
const USER_KEY = 'current_user';

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly API = '/api/auth';
  private currentUser = new BehaviorSubject<AuthResponse | null>(this.loadUser());

  user$ = this.currentUser.asObservable();

  constructor(private http: HttpClient) {}

  get token(): string | null {
    return localStorage.getItem(TOKEN_KEY);
  }

  get refreshToken(): string | null {
    return localStorage.getItem(REFRESH_KEY);
  }

  get user(): AuthResponse | null {
    return this.currentUser.value;
  }

  isAdmin(): boolean {
    return this.user?.role === 'ADMIN';
  }

  isAuthenticated(): boolean {
    return !!this.token;
  }

  register(payload: { fullName: string; email: string; password: string; asAdmin: boolean }): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API}/register`, payload).pipe(tap(r => this.saveSession(r)));
  }

  login(payload: { email: string; password: string }): Observable<AuthResponse> {
    return this.http.post<AuthResponse>(`${this.API}/login`, payload).pipe(tap(r => this.saveSession(r)));
  }

  refresh(): Observable<AuthResponse> {
    const refreshToken = this.refreshToken;
    if (!refreshToken) throw new Error('No refresh token');
    return this.http.post<AuthResponse>(`${this.API}/refresh`, { refreshToken }).pipe(tap(r => this.saveSession(r)));
  }

  logout(): void {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(REFRESH_KEY);
    localStorage.removeItem(USER_KEY);
    this.currentUser.next(null);
  }

  private saveSession(r: AuthResponse): void {
    localStorage.setItem(TOKEN_KEY, r.accessToken);
    localStorage.setItem(REFRESH_KEY, r.refreshToken);
    localStorage.setItem(USER_KEY, JSON.stringify(r));
    this.currentUser.next(r);
  }

  private loadUser(): AuthResponse | null {
    try {
      const raw = localStorage.getItem(USER_KEY);
      return raw ? JSON.parse(raw) : null;
    } catch {
      return null;
    }
  }
}
