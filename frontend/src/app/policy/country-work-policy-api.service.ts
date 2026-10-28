import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { switchMap, timeout } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { CountryWorkPolicy, CountryWorkPolicyInput, CountryWorkPolicyPage } from './country-work-policy.types';

@Injectable({ providedIn: 'root' })
export class CountryWorkPolicyApi {
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly base = '/api/hr/work-policies';
  current(countryCode: string, on: string) {
    return this.http.get<CountryWorkPolicy | null>(`${this.base}/current`, { params: { countryCode, on } }).pipe(timeout(15000));
  }
  history(countryCode: string, page: number) {
    return this.http.get<CountryWorkPolicyPage>(this.base, { params: { countryCode, page, size: 20 } }).pipe(timeout(15000));
  }
  create(input: CountryWorkPolicyInput) {
    return this.auth.csrf().pipe(switchMap(csrf => this.http.post<CountryWorkPolicy>(this.base, input,
      { headers: { [csrf.headerName]: csrf.token } }).pipe(timeout(15000))));
  }
}
