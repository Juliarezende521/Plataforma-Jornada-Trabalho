import { Component, DestroyRef, ElementRef, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Subscription, timeout } from 'rxjs';
import { AuthService, Identity } from '../auth/auth.service';
import { HistoryComponent } from '../history/history.component';
import { EmployeeSummary } from '../history/history.types';
import { formatInstant, formatWorkDate } from '../journey/journey-format';
import { HrComponent } from './hr.component';
import { AdjustmentReviewComponent } from '../adjustment/adjustment-review.component';
import { AuditLogComponent } from '../audit/audit-log.component';
import { CountryWorkPolicyComponent } from '../policy/country-work-policy.component';

export interface OverviewPerson extends EmployeeSummary { state: 'WORKING' | 'PAUSED' | 'CLOSED' | 'NOT_STARTED'; workDate: string | null; }
export interface OverviewPage { items: OverviewPerson[]; page: number; size: number; totalElements: number; observedAtUtc: string; }

@Component({ selector: 'app-hr-dashboard', imports: [HrComponent, HistoryComponent, AdjustmentReviewComponent, AuditLogComponent, CountryWorkPolicyComponent],
  templateUrl: './hr-dashboard.component.html', styleUrl: './hr-dashboard.component.scss' })
export class HrDashboardComponent {
  readonly user = input.required<Identity>();
  readonly view = signal<'overview' | 'individual' | 'selected' | 'adjustments' | 'audit' | 'policies'>('overview');
  readonly page = signal<OverviewPage | null>(null);
  readonly selected = signal<OverviewPerson | null>(null);
  readonly busy = signal(false);
  readonly reportBusy = signal(false);
  readonly reportCompetence = signal(previousMonth());
  readonly searchTerm = signal('');
  readonly message = signal('');
  readonly labels = { WORKING: 'Trabalhando', PAUSED: 'Em pausa', CLOSED: 'Encerrada', NOT_STARTED: 'Sem jornada iniciada' };
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly individual = viewChild(HrComponent);
  private readonly history = viewChild(HistoryComponent);
  private readonly adjustments = viewChild(AdjustmentReviewComponent);
  private readonly audit = viewChild(AuditLogComponent);
  private readonly policies = viewChild(CountryWorkPolicyComponent);
  private request?: Subscription;
  private reportRequest?: Subscription;
  readonly formatWorkDate = formatWorkDate;
  constructor() {
    this.destroy.onDestroy(() => { this.request?.unsubscribe(); this.reportRequest?.unsubscribe(); });
    effect(() => { const user = this.user(); untracked(() => {
      this.request?.unsubscribe(); this.reportRequest?.unsubscribe(); this.busy.set(false); this.reportBusy.set(false);
      this.page.set(null); this.selected.set(null); this.view.set('overview'); this.searchTerm.set('');
      if (user.hrReader) this.load(0);
    }); });
  }
  load(page: number): void {
    if (!this.user().hrReader || this.busy() || page < 0) return;
    this.page.set(null); this.busy.set(true); this.message.set('Consultando estados das jornadas…');
    const query = this.searchTerm().trim();
    const params: Record<string, string | number> = { page, size: 20 };
    if (query) params['query'] = query;
    this.request = this.http.get<OverviewPage>('/api/hr/employees/overview', { params }).pipe(timeout(15000)).subscribe({
      next: result => { this.page.set(result); this.busy.set(false); this.message.set(result.items.length ? 'Consulta concluída.' : 'Nenhum colaborador disponível.'); },
      error: (error: unknown) => { this.busy.set(false); this.page.set(null);
        if (error instanceof HttpErrorResponse && error.status === 401) this.auth.invalidate();
        this.message.set(error instanceof HttpErrorResponse && error.status === 403 ? 'Seu acesso de RH não está disponível. Verifique seu acesso.' : 'Não foi possível consultar as jornadas. Tente novamente.'); },
    });
  }
  show(view: 'overview' | 'individual' | 'adjustments' | 'audit' | 'policies'): void { this.selected.set(null); this.view.set(view); this.focus(); }
  open(person: OverviewPerson): void { this.selected.set(person); this.view.set('selected'); this.focus(); }
  private focus(): void { setTimeout(() => {
    if (this.destroy.destroyed) return;
    if (this.view() === 'individual') this.individual()?.focusHeading();
    else if (this.view() === 'adjustments') this.adjustments()?.focusHeading();
    else if (this.view() === 'audit') this.audit()?.focusHeading();
    else if (this.view() === 'policies') this.policies()?.focusHeading();
    else if (this.view() === 'selected') this.history()?.focusHeading();
    else this.heading()?.nativeElement.focus();
  }); }
  observed(instant: string): string { return formatInstant(instant, this.user().referenceTimeZoneId); }
  clearSearch(): void { this.searchTerm.set(''); this.load(0); }

  downloadConsolidatedReport(): void {
    const competence = this.reportCompetence();
    if (this.reportBusy() || !/^\d{4}-(0[1-9]|1[0-2])$/.test(competence)) {
      this.message.set('Informe uma competência válida.');
      return;
    }
    this.reportBusy.set(true); this.message.set('Preparando relatório consolidado…');
    this.reportRequest?.unsubscribe();
    this.reportRequest = this.http.get('/api/hr/monthly-closures/report', {
      params: { competence }, responseType: 'blob',
    }).pipe(timeout(30000)).subscribe({
      next: blob => {
        const url = URL.createObjectURL(blob); const link = document.createElement('a');
        link.href = url; link.download = `fechamentos-${competence}.csv`; link.click(); URL.revokeObjectURL(url);
        this.reportBusy.set(false); this.message.set('Relatório consolidado baixado.');
      },
      error: (error: unknown) => {
        this.reportBusy.set(false);
        if (error instanceof HttpErrorResponse && error.status === 401) this.auth.invalidate();
        this.message.set(error instanceof HttpErrorResponse && error.status === 403
          ? 'Seu acesso de RH não está disponível. Verifique seu acesso.'
          : 'Não foi possível gerar o relatório consolidado. Tente novamente.');
      },
    });
  }
}

function previousMonth(): string {
  const date = new Date();
  date.setDate(1); date.setMonth(date.getMonth() - 1);
  return `${date.getFullYear().toString().padStart(4, '0')}-${(date.getMonth() + 1).toString().padStart(2, '0')}`;
}
