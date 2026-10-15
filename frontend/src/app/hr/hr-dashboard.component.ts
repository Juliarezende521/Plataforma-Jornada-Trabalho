import { Component, DestroyRef, ElementRef, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Subscription, timeout } from 'rxjs';
import { AuthService, Identity } from '../auth/auth.service';
import { HistoryComponent } from '../history/history.component';
import { EmployeeSummary } from '../history/history.types';
import { formatInstant, formatWorkDate } from '../journey/journey-format';
import { HrComponent } from './hr.component';

export interface OverviewPerson extends EmployeeSummary { state: 'WORKING' | 'PAUSED' | 'CLOSED' | 'NOT_STARTED'; workDate: string | null; }
export interface OverviewPage { items: OverviewPerson[]; page: number; size: number; totalElements: number; observedAtUtc: string; }

@Component({ selector: 'app-hr-dashboard', imports: [HrComponent, HistoryComponent],
  templateUrl: './hr-dashboard.component.html', styleUrl: './hr-dashboard.component.scss' })
export class HrDashboardComponent {
  readonly user = input.required<Identity>();
  readonly view = signal<'overview' | 'individual' | 'selected'>('overview');
  readonly page = signal<OverviewPage | null>(null);
  readonly selected = signal<EmployeeSummary | null>(null);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly labels = { WORKING: 'Trabalhando', PAUSED: 'Em pausa', CLOSED: 'Encerrada', NOT_STARTED: 'Sem jornada iniciada' };
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly individual = viewChild(HrComponent);
  private readonly history = viewChild(HistoryComponent);
  private request?: Subscription;
  readonly formatWorkDate = formatWorkDate;
  constructor() {
    this.destroy.onDestroy(() => this.request?.unsubscribe());
    effect(() => { const user = this.user(); untracked(() => {
      this.request?.unsubscribe(); this.busy.set(false); this.page.set(null); this.selected.set(null); this.view.set('overview');
      if (user.hrReader) this.load(0);
    }); });
  }
  load(page: number): void {
    if (!this.user().hrReader || this.busy() || page < 0) return;
    this.page.set(null); this.busy.set(true); this.message.set('Consultando estados das jornadas…');
    this.request = this.http.get<OverviewPage>('/api/hr/employees/overview', { params: { page, size: 20 } }).pipe(timeout(15000)).subscribe({
      next: result => { this.page.set(result); this.busy.set(false); this.message.set(result.items.length ? 'Consulta concluída.' : 'Nenhum colaborador disponível.'); },
      error: (error: unknown) => { this.busy.set(false); this.page.set(null);
        if (error instanceof HttpErrorResponse && error.status === 401) this.auth.invalidate();
        this.message.set(error instanceof HttpErrorResponse && error.status === 403 ? 'Seu acesso de RH não está disponível. Verifique seu acesso.' : 'Não foi possível consultar as jornadas. Tente novamente.'); },
    });
  }
  show(view: 'overview' | 'individual'): void { this.selected.set(null); this.view.set(view); this.focus(); }
  open(person: OverviewPerson): void { this.selected.set(person); this.view.set('selected'); this.focus(); }
  private focus(): void { setTimeout(() => {
    if (this.destroy.destroyed) return;
    if (this.view() === 'individual') this.individual()?.focusHeading();
    else if (this.view() === 'selected') this.history()?.focusHeading();
    else this.heading()?.nativeElement.focus();
  }); }
  observed(instant: string): string { return formatInstant(instant, this.user().referenceTimeZoneId); }
}
