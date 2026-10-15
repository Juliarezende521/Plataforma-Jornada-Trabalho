import { inject, Injectable } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { catchError, map, Observable, of, switchMap, throwError, timeout } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { Attempt, CurrentJourney, Receipt } from './journey.types';

@Injectable({ providedIn: 'root' })
export class JourneyApi {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  current(): Observable<CurrentJourney> {
    return this.http.get<CurrentJourney>('/api/me/work-sessions/current').pipe(timeout(8000));
  }

  receipt(key: string): Observable<Receipt | null> {
    return this.http.get<Receipt>('/api/me/work-event-requests/' + encodeURIComponent(key)).pipe(timeout(8000),
      catchError((error: unknown) => error instanceof HttpErrorResponse && error.status === 404
        ? of(null) : throwError(() => error)));
  }

  mark(attempt: Attempt): Observable<Receipt> {
    const command = { type: attempt.type, expectedWorkSessionId: attempt.expectedWorkSessionId };
    // Replay the preserved zone, even if the browser moved to another zone since the first POST.
    const payload = attempt.timeZoneId === undefined ? command : { ...command, timeZoneId: attempt.timeZoneId };
    return this.auth.csrf().pipe(switchMap(csrf => this.http.post<Receipt>('/api/me/work-events', payload,
      { headers: { [csrf.headerName]: csrf.token, 'Idempotency-Key': attempt.requestKey } }).pipe(timeout(20000))),
      catchError((error: unknown) => {
        if (error instanceof HttpErrorResponse && error.status === 409 && this.isReceipt(error.error, attempt) && error.error.httpStatus === 409) return of(error.error);
        return throwError(() => error);
      }), map(receipt => {
        if (!this.isReceipt(receipt, attempt)) throw new Error('Unexpected marking response');
        return receipt;
      }));
  }

  isReceipt(value: unknown, attempt: Attempt): value is Receipt {
    if (typeof value !== 'object' || value === null) return false;
    const receipt = value as Partial<Receipt>;
    const uuid = (id: unknown) => typeof id === 'string' && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(id);
    const instant = (value: unknown) => typeof value === 'string' && Number.isFinite(Date.parse(value));
    const rejectionCodes = ['ALREADY_OPEN', 'NO_OPEN_SESSION', 'TARGET_CHANGED', 'INVALID_TRANSITION', 'CLOCK_REGRESSION', 'KEY_REUSED'];
    return receipt.requestKey === attempt.requestKey && receipt.type === attempt.type && instant(receipt.confirmedAtUtc) &&
      (receipt.httpStatus === 201 ? receipt.code === 'RECORDED' && uuid(receipt.workSessionId) && uuid(receipt.eventId) && instant(receipt.occurredAtUtc) :
        receipt.httpStatus === 409 && rejectionCodes.includes(receipt.code ?? '') && receipt.workSessionId === null &&
          receipt.eventId === null && receipt.occurredAtUtc === null);
  }
}
