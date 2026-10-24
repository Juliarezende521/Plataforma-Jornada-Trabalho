import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { formatInstant } from '../journey/journey-format';
import { MonthlyClosureApi } from './monthly-closure-api.service';
import { MonthlyClosure, MonthlyClosurePage } from './monthly-closure.types';

@Component({
  selector: 'app-monthly-closure',
  imports: [ReactiveFormsModule],
  templateUrl: './monthly-closure.component.html',
  styleUrl: './monthly-closure.component.scss',
})
export class MonthlyClosureComponent {
  readonly employeeId = input.required<string>();
  readonly timeZone = input.required<string>();
  private readonly api = inject(MonthlyClosureApi);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  private request?: Subscription;
  private key = '';
  readonly competence = new FormControl(previousMonth(), {
    nonNullable: true,
    validators: [Validators.required, Validators.pattern(/^\d{4}-(0[1-9]|1[0-2])$/)],
  });
  readonly selected = signal<MonthlyClosure | null>(null);
  readonly history = signal<MonthlyClosurePage | null>(null);
  readonly busy = signal(false);
  readonly message = signal('Selecione uma competência encerrada para consultar ou fechar.');

  constructor() {
    this.destroy.onDestroy(() => this.request?.unsubscribe());
    effect(() => {
      const id = this.employeeId();
      untracked(() => {
        if (id === this.key) return;
        this.key = id;
        this.request?.unsubscribe();
        this.selected.set(null);
        this.history.set(null);
        this.busy.set(false);
      });
    });
  }

  consult(): void {
    if (!this.validCompetence()) return;
    this.start(this.api.find(this.employeeId(), this.competence.value).subscribe({
      next: value => {
        this.selected.set(value);
        this.busy.set(false);
        this.message.set(value ? 'Fechamento mensal carregado.' : 'A competência ainda está aberta.');
      },
      error: error => this.fail(error),
    }));
  }

  close(): void {
    if (!this.validCompetence()) return;
    this.start(this.api.close(this.employeeId(), this.competence.value).subscribe({
      next: value => {
        this.selected.set(value);
        this.history.set(null);
        this.busy.set(false);
        this.message.set('Competência fechada com sucesso.');
      },
      error: error => this.fail(error),
    }));
  }

  loadHistory(page = 0): void {
    if (this.busy() || page < 0) return;
    this.start(this.api.history(this.employeeId(), page).subscribe({
      next: value => {
        this.history.set(value);
        this.busy.set(false);
        this.message.set(value.totalElements ? 'Histórico de fechamentos carregado.' : 'Nenhum fechamento realizado.');
      },
      error: error => this.fail(error),
    }));
  }

  duration(seconds: number): string {
    const sign = seconds < 0 ? '−' : '';
    const absolute = Math.abs(seconds);
    const hours = Math.floor(absolute / 3600);
    const minutes = Math.floor((absolute % 3600) / 60);
    return `${sign}${hours} h${minutes ? ` ${minutes} min` : ''}`;
  }

  closedAt(value: string): string {
    return formatInstant(value, this.timeZone());
  }

  private validCompetence(): boolean {
    this.competence.markAsTouched();
    if (this.competence.invalid) {
      this.message.set('Informe uma competência válida.');
      return false;
    }
    return true;
  }

  private start(request: Subscription): void {
    this.request?.unsubscribe();
    this.busy.set(true);
    this.message.set('Processando fechamento mensal…');
    this.request = request;
  }

  private fail(error: unknown): void {
    this.busy.set(false);
    const response = error instanceof HttpErrorResponse ? error : null;
    if (response?.status === 401) {
      this.auth.invalidate();
      return;
    }
    const title = response?.error?.title;
    const messages: Record<string, string> = {
      'Monthly period has not finished': 'A competência só pode ser fechada depois do último dia do mês.',
      'Monthly period is already closed': 'Esta competência já foi fechada.',
      'Monthly period has open work sessions': 'Existem jornadas abertas nessa competência.',
      'Monthly period has incomplete schedule coverage': 'A escala não cobre todos os dias da competência.',
      'Monthly period has pending adjustments': 'Existem ajustes pendentes nessa competência.',
    };
    this.message.set(response?.status === 403 ? 'Seu acesso não permite realizar fechamentos.'
      : messages[title] ?? 'Não foi possível concluir a operação. Tente novamente.');
  }
}

function previousMonth(): string {
  const date = new Date();
  return new Date(Date.UTC(date.getUTCFullYear(), date.getUTCMonth() - 1, 1)).toISOString().slice(0, 7);
}
