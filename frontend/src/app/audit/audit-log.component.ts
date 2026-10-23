import { Component, DestroyRef, ElementRef, inject, input, signal, viewChild } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { formatInstant } from '../journey/journey-format';
import { AuditApi } from './audit-api.service';
import { AuditPage } from './audit.types';

@Component({ selector: 'app-audit-log', templateUrl: './audit-log.component.html', styleUrl: './audit-log.component.scss' })
export class AuditLogComponent {
  readonly timeZone = input('UTC');
  readonly page = signal<AuditPage | null>(null);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly actions: Record<string, string> = {
    ADJUSTMENT_REQUESTED: 'Ajuste solicitado',
    ADJUSTMENT_APPROVED: 'Ajuste aprovado',
    ADJUSTMENT_REJECTED: 'Ajuste rejeitado',
    WORK_SCHEDULE_CREATED: 'Escala cadastrada',
    WORK_SCHEDULE_EXCEPTION_CREATED: 'Exceção de escala cadastrada',
  };
  readonly entities: Record<string, string> = {
    WORK_ADJUSTMENT: 'Ajuste de jornada',
    WORK_SCHEDULE: 'Escala',
    WORK_SCHEDULE_EXCEPTION: 'Exceção de escala',
  };
  private readonly api = inject(AuditApi);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private request?: Subscription;

  constructor() { this.destroy.onDestroy(() => this.request?.unsubscribe()); this.load(0); }
  focusHeading(): void { this.heading()?.nativeElement.focus(); }
  load(page: number): void {
    if (this.busy() || page < 0) return;
    this.request?.unsubscribe();
    this.busy.set(true);
    this.message.set('Consultando registros de auditoria…');
    this.request = this.api.history(page).subscribe({
      next: result => {
        this.page.set(result);
        this.busy.set(false);
        this.message.set(result.totalElements ? 'Registros de auditoria carregados.' : 'Nenhum registro de auditoria disponível.');
      },
      error: error => this.fail(error),
    });
  }
  action(value: string): string { return this.actions[value] ?? value; }
  entity(value: string): string { return this.entities[value] ?? value; }
  date(value: string): string { return formatInstant(value, this.timeZone()); }
  private fail(error: unknown): void {
    this.busy.set(false);
    this.page.set(null);
    if (error instanceof HttpErrorResponse && error.status === 401) { this.auth.invalidate(); return; }
    this.message.set(error instanceof HttpErrorResponse && error.status === 403
      ? 'Seu acesso não permite consultar a auditoria.' : 'Não foi possível consultar a auditoria. Tente novamente.');
  }
}
