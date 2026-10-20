import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { switchMap, timeout } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { EventType } from '../journey/journey.types';
import { AdjustmentPage, AdjustmentStatus, WorkAdjustment } from './adjustment.types';

@Injectable({ providedIn: 'root' })
export class AdjustmentApi {
  private readonly http=inject(HttpClient);
  private readonly auth=inject(AuthService);
  mine(workSessionId: string) {
    return this.http.get<AdjustmentPage>('/api/me/work-adjustments', { params: { workSessionId, page: 0, size: 20 } }).pipe(timeout(15000));
  }
  pending(page=0) {
    return this.http.get<AdjustmentPage>('/api/hr/work-adjustments', { params: { page, size: 20 } }).pipe(timeout(15000));
  }
  request(payload: { workSessionId: string; requestedEventType: EventType; proposedOccurredAtUtc: string; reason: string }) {
    return this.auth.csrf().pipe(switchMap(csrf => this.http.post<WorkAdjustment>('/api/me/work-adjustments', payload,
      { headers: { [csrf.headerName]: csrf.token } }).pipe(timeout(15000))));
  }
  decide(id: string, decision: Exclude<AdjustmentStatus, 'PENDING'>, note: string | null) {
    return this.auth.csrf().pipe(switchMap(csrf => this.http.post<WorkAdjustment>(
      '/api/hr/work-adjustments/' + encodeURIComponent(id) + '/decision', { decision, note },
      { headers: { [csrf.headerName]: csrf.token } }).pipe(timeout(15000))));
  }
}
