import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap, timeout } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import {
  EmploymentContract,
  EmploymentContractInput,
  EmploymentContractPage,
} from './employment-contract.types';
@Injectable({ providedIn: 'root' })
export class EmploymentContractApi {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  current(employeeId: string) {
    return this.http
      .get<EmploymentContract | null>(
        `/api/hr/employees/${encodeURIComponent(employeeId)}/employment-contract`,
      )
      .pipe(timeout(15000));
  }
  history(employeeId: string, page = 0) {
    return this.http
      .get<EmploymentContractPage>(
        `/api/hr/employees/${encodeURIComponent(employeeId)}/employment-contracts`,
        { params: { page, size: 20 } },
      )
      .pipe(timeout(15000));
  }
  create(employeeId: string, input: EmploymentContractInput) {
    return this.auth
      .csrf()
      .pipe(
        switchMap((csrf) =>
          this.http
            .post<EmploymentContract>(
              `/api/hr/employees/${encodeURIComponent(employeeId)}/employment-contracts`,
              input,
              { headers: { [csrf.headerName]: csrf.token } },
            )
            .pipe(timeout(15000)),
        ),
      );
  }
}
