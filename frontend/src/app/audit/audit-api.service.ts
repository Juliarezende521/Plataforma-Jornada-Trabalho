import { HttpClient } from '@angular/common/http';
import { inject, Injectable } from '@angular/core';
import { timeout } from 'rxjs';
import { AuditPage } from './audit.types';

@Injectable({ providedIn: 'root' })
export class AuditApi {
  private readonly http = inject(HttpClient);
  history(page = 0) {
    return this.http.get<AuditPage>('/api/hr/audit-entries', { params: { page, size: 20 } }).pipe(timeout(15000));
  }
}
