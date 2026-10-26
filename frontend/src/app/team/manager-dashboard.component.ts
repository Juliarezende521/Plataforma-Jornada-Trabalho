import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { Subscription, timeout } from 'rxjs';
import { AuthService, Identity } from '../auth/auth.service';
import { HistoryComponent } from '../history/history.component';
import { EmployeePage, EmployeeSummary } from '../history/history.types';
import { AdjustmentReviewComponent } from '../adjustment/adjustment-review.component';

@Component({
  selector: 'app-manager-dashboard',
  imports: [HistoryComponent, AdjustmentReviewComponent],
  templateUrl: './manager-dashboard.component.html',
  styleUrl: './manager-dashboard.component.scss',
})
export class ManagerDashboardComponent {
  readonly user = input.required<Identity>();
  readonly team = signal<EmployeePage | null>(null);
  readonly selected = signal<EmployeeSummary | null>(null);
  readonly view = signal<'team'|'adjustments'>('team');
  readonly busy = signal(false);
  readonly message = signal('');
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private readonly history = viewChild(HistoryComponent);
  private request?: Subscription;

  constructor() {
    this.destroy.onDestroy(() => this.request?.unsubscribe());
    this.load(0);
  }

  focusHeading(): void { this.heading()?.nativeElement.focus(); }

  show(view: 'team'|'adjustments'): void { this.selected.set(null); this.view.set(view); }

  load(page: number): void {
    if (this.busy() || page < 0) return;
    this.busy.set(true); this.message.set('Consultando sua equipe…');
    this.request = this.http.get<EmployeePage>('/api/manager/team', { params: { page, size: 20 } }).pipe(timeout(15000)).subscribe({
      next: result => { this.team.set(result); this.busy.set(false); this.message.set(result.items.length ? 'Equipe consultada.' : 'Nenhum colaborador vinculado à sua equipe.'); },
      error: (error: unknown) => { this.busy.set(false); if (error instanceof HttpErrorResponse && error.status === 401) this.auth.invalidate();
        this.message.set(error instanceof HttpErrorResponse && error.status === 403 ? 'Seu acesso de gestor não está disponível.' : 'Não foi possível consultar sua equipe. Tente novamente.'); },
    });
  }

  open(person: EmployeeSummary): void {
    this.selected.set(person);
    setTimeout(() => { if (!this.destroy.destroyed) this.history()?.focusHeading(); });
  }

  back(): void { this.selected.set(null); setTimeout(() => this.focusHeading()); }
}
