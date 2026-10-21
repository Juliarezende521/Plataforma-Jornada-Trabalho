import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { timeout } from 'rxjs';
import { HistoryFilter, HistoryPage, JourneyDetail, PeriodSummary } from './history.types';
@Injectable({ providedIn: 'root' })
export class HistoryApi {
  private readonly http = inject(HttpClient);
  history(filter: HistoryFilter, page: number, employeeId?: string) {
    return this.http.get<HistoryPage>(this.base(employeeId), { params: { ...filter, page, size: 20 } }).pipe(timeout(15000));
  }
  summary(filter:HistoryFilter,employeeId?:string) {
    return this.http.get<PeriodSummary>(this.base(employeeId)+'/summary',{params:{from:filter.from,to:filter.to}}).pipe(timeout(15000));
  }
  detail(id: string, employeeId?: string) { return this.http.get<JourneyDetail>(this.base(employeeId) + '/' + encodeURIComponent(id)).pipe(timeout(15000)); }
  private base(employeeId?: string) { return employeeId ? '/api/hr/employees/' + encodeURIComponent(employeeId) + '/work-sessions' : '/api/me/work-sessions'; }
}
