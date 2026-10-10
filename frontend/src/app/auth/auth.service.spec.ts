import { TestBed } from '@angular/core/testing';
import { HttpParams, provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuthService, Identity } from './auth.service';
import { vi } from 'vitest';

const identity: Identity = { userId: 'user-br', employeeId: 'employee-br', email: 'brasil@example.invalid', name: 'Brasil', referenceTimeZoneId: 'America/Sao_Paulo' };

describe('AuthService', () => {
  let service: AuthService;
  let http: HttpTestingController;
  beforeEach(() => {
    TestBed.configureTestingModule({ providers: [provideHttpClient(), provideHttpClientTesting()] });
    service = TestBed.inject(AuthService);
    http = TestBed.inject(HttpTestingController);
  });
  afterEach(() => http.verify());

  it('uses form encoding, preserves password and renews CSRF before reading identity', () => {
    let completed = false;
    service.login(' brasil+tag@example.invalid ', ' senha &+= ').subscribe(() => completed = true);
    http.expectNone('/api/auth/login');
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'before-login' });
    const login = http.expectOne('/api/auth/login');
    expect(login.request.method).toBe('POST');
    expect(login.request.headers.get('X-CSRF-TOKEN')).toBe('before-login');
    const body = login.request.body as HttpParams;
    expect(body.get('email')).toBe('brasil+tag@example.invalid');
    expect(body.get('password')).toBe(' senha &+= ');
    expect(login.request.serializeBody()).toContain('%26');
    login.flush(null, { status: 204, statusText: 'No Content' });
    http.expectNone('/api/me');
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'after-login' });
    http.expectOne('/api/me').flush(identity);
    expect(service.user()).toEqual(identity);
    expect(completed).toBe(true);
  });

  it('does not send credentials if CSRF retrieval fails', () => {
    let failed = false;
    service.login('brasil@example.invalid', 'secret').subscribe({ error: () => failed = true });
    http.expectOne('/api/auth/csrf').flush({}, { status: 503, statusText: 'Unavailable' });
    http.expectNone('/api/auth/login');
    expect(failed).toBe(true);
  });

  it('does not replay a login rejected by CSRF', () => {
    let failed = false;
    service.login('brasil@example.invalid', 'secret').subscribe({ error: () => failed = true });
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'token' });
    http.expectOne('/api/auth/login').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(failed).toBe(true);
    http.expectNone('/api/auth/login');
    http.expectNone('/api/auth/csrf');
  });

  it('clears an expired session when the backend returns 401', () => {
    service.refresh().subscribe();
    http.expectOne('/api/me').flush(identity);
    service.refresh().subscribe(value => expect(value).toBeNull());
    http.expectOne('/api/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    expect(service.user()).toBeNull();
  });

  it('hides stale identity and propagates unavailability rather than treating it as anonymous', () => {
    service.refresh().subscribe();
    http.expectOne('/api/me').flush(identity);
    let failed = false;
    service.refresh().subscribe({ error: () => failed = true });
    http.expectOne('/api/me').flush({}, { status: 503, statusText: 'Unavailable' });
    expect(failed).toBe(true);
    expect(service.user()).toBeNull();
  });

  it('only clears identity after confirmed logout and uses a fresh CSRF token', () => {
    service.refresh().subscribe();
    http.expectOne('/api/me').flush(identity);
    service.logout().subscribe({ error: () => {} });
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'fresh' });
    const first = http.expectOne('/api/auth/logout');
    expect(first.request.headers.get('X-CSRF-TOKEN')).toBe('fresh');
    first.flush({}, { status: 503, statusText: 'Unavailable' });
    expect(service.user()).toEqual(identity);
    service.logout().subscribe();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'fresh-again' });
    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    expect(service.user()).toBeNull();
  });

  it('times out a stalled session request without leaving a stale identity', () => {
    vi.useFakeTimers();
    try {
      let failed = false;
      service.refresh().subscribe({ error: () => failed = true });
      const request = http.expectOne('/api/me');
      vi.advanceTimersByTime(8001);
      expect(failed).toBe(true);
      expect(request.cancelled).toBe(true);
      expect(service.user()).toBeNull();
    } finally { vi.useRealTimers(); }
  });
});
