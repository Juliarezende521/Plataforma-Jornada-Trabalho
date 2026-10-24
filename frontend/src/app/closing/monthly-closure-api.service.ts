import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap, timeout } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { MonthlyClosure, MonthlyClosurePage } from './monthly-closure.types';

@Injectable({ providedIn: 'root' })
export class MonthlyClosureApi {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private base(employeeId: string): string {
    return `/api/hr/employees/${encodeURIComponent(employeeId)}/monthly-closures`;
  }
  find(employeeId: string, competence: string) {
    return this.http.get<MonthlyClosure | null>(`${this.base(employeeId)}/${competence}`).pipe(timeout(30000));
  }
  history(employeeId: string, page: number) {
    return this.http.get<MonthlyClosurePage>(this.base(employeeId), { params: { page, size: 20 } }).pipe(timeout(30000));
  }
  close(employeeId: string, competence: string) {
    return this.auth.csrf().pipe(switchMap(csrf => this.http.post<MonthlyClosure>(
      `${this.base(employeeId)}/${competence}`, null,
      { headers: { [csrf.headerName]: csrf.token } },
    ).pipe(timeout(30000))));
  }
}
