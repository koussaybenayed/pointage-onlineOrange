import { Injectable } from '@angular/core';
import { HttpEvent, HttpHandler, HttpInterceptor, HttpRequest, HttpErrorResponse } from '@angular/common/http';
import { Observable, throwError } from 'rxjs';
import { catchError, switchMap } from 'rxjs/operators';
import { AuthService } from './auth.service';

let isRefreshing = false;

@Injectable()
export class AuthInterceptor implements HttpInterceptor {

  constructor(private auth: AuthService) {}

  intercept(req: HttpRequest<any>, next: HttpHandler): Observable<HttpEvent<any>> {
    const token = this.auth.token;
    let authReq = req;

    if (token && !req.url.includes('/api/auth')) {
      authReq = req.clone({ setHeaders: { Authorization: `Bearer ${token}` } });
    }

    return next.handle(authReq).pipe(
      catchError((error: HttpErrorResponse) => {
        if (error.status === 401 && !isRefreshing && !req.url.includes('/api/auth')) {
          isRefreshing = true;
          return this.auth.refresh().pipe(
            switchMap((r) => {
              isRefreshing = false;
              const retry = req.clone({ setHeaders: { Authorization: `Bearer ${r.accessToken}` } });
              return next.handle(retry);
            }),
            catchError((refreshErr) => {
              isRefreshing = false;
              this.auth.logout();
              return throwError(() => refreshErr);
            })
          );
        }
        return throwError(() => error);
      })
    );
  }
}
