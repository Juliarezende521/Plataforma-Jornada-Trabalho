import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';
import { HistoryFilter, HistoryPage, JourneyDetail } from './history.types';
@Injectable({ providedIn: 'root' })
export class HistoryApi {
  private readonly http = inject(HttpClient);
  history(filter: HistoryFilter, page: number) {
    return this.http.get<HistoryPage>('/api/me/work-sessions', { params: { ...filter, page, size: 20 } }).pipe(timeout(15000));
  }
  detail(id: string) { return this.http.get<JourneyDetail>('/api/me/work-sessions/' + encodeURIComponent(id)).pipe(timeout(15000)); }
}
