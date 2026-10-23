import { inject, Injectable } from '@angular/core';
import { HttpClient } from '@angular/common/http';
import { switchMap, timeout } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { ScheduleExceptionInput, ScheduleExceptionPage, WorkSchedule, WorkScheduleInput, WorkSchedulePage } from './work-schedule.types';

@Injectable({ providedIn: 'root' })
export class WorkScheduleApi {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);

  get(employeeId?: string) {
    const url = employeeId
      ? `/api/hr/employees/${encodeURIComponent(employeeId)}/work-schedule`
      : '/api/me/work-schedule';
    return this.http.get<WorkSchedule | null>(url).pipe(timeout(15000));
  }

  create(employeeId: string, input: WorkScheduleInput) {
    return this.auth.csrf().pipe(switchMap(csrf => this.http.post<WorkSchedule>(
      `/api/hr/employees/${encodeURIComponent(employeeId)}/work-schedules`, input,
      { headers: { [csrf.headerName]: csrf.token } },
    ).pipe(timeout(15000))));
  }
  history(employeeId: string, page = 0) {
    return this.http.get<WorkSchedulePage>(`/api/hr/employees/${encodeURIComponent(employeeId)}/work-schedules`,
      { params: { page, size: 20 } }).pipe(timeout(15000));
  }
  exceptionHistory(employeeId: string, page = 0) {
    return this.http.get<ScheduleExceptionPage>(
      `/api/hr/employees/${encodeURIComponent(employeeId)}/work-schedule-exceptions`,
      { params: { page, size: 20 } },
    ).pipe(timeout(15000));
  }

  createException(employeeId: string, input: ScheduleExceptionInput) {
    return this.auth.csrf().pipe(switchMap(csrf => this.http.post(
      `/api/hr/employees/${encodeURIComponent(employeeId)}/work-schedule-exceptions`, input,
      { headers: { [csrf.headerName]: csrf.token } },
    ).pipe(timeout(15000))));
  }
}
