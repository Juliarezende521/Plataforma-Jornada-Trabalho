import { inject, Injectable, signal } from '@angular/core';
import { HttpClient, HttpErrorResponse, HttpParams } from '@angular/common/http';
import { catchError, Observable, of, switchMap, tap, throwError, timeout } from 'rxjs';

export interface Identity {
  userId: string;
  employeeId: string;
  email: string;
  name: string;
  referenceTimeZoneId: string;
}
interface CsrfToken { headerName: string; token: string; }
const REQUEST_TIMEOUT_MS = 8000;

@Injectable({ providedIn: 'root' })
export class AuthService {
  private readonly http = inject(HttpClient);
  private readonly identity = signal<Identity | null>(null);
  readonly user = this.identity.asReadonly();

  refresh(): Observable<Identity | null> {
    return this.http.get<Identity>('/api/me').pipe(timeout(REQUEST_TIMEOUT_MS),
      tap((user) => this.identity.set(user)),
      catchError((error: unknown) => {
        this.identity.set(null);
        return error instanceof HttpErrorResponse && error.status === 401
          ? of(null) : throwError(() => error);
      }));
  }

  login(email: string, password: string): Observable<Identity | null> {
    const body = new HttpParams().set('email', email.trim()).set('password', password);
    return this.postWithCsrf('/api/auth/login', body).pipe(
      // Authentication invalidates the previous token. Never reuse it or replay POSTs.
      switchMap(() => this.csrf()),
      switchMap(() => this.refresh()));
  }

  logout(): Observable<void> {
    return this.postWithCsrf('/api/auth/logout', null).pipe(
      tap(() => this.identity.set(null)));
  }

  private postWithCsrf(url: string, body: HttpParams | null): Observable<void> {
    return this.csrf().pipe(
      switchMap((csrf) => this.http.post<void>(url, body, {
        headers: { [csrf.headerName]: csrf.token },
      }).pipe(timeout(REQUEST_TIMEOUT_MS))));
  }

  private csrf(): Observable<CsrfToken> {
    // Relative URLs keep cookies same-origin; no persistent browser token storage.
    return this.http.get<CsrfToken>('/api/auth/csrf').pipe(timeout(REQUEST_TIMEOUT_MS));
  }
}
