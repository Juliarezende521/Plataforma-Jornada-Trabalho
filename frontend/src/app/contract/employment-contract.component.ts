import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { formatWorkDate } from '../journey/journey-format';
import { EmploymentContractApi } from './employment-contract-api.service';
import { EmploymentContract, EmploymentContractPage } from './employment-contract.types';
@Component({
  selector: 'app-employment-contract',
  imports: [ReactiveFormsModule],
  templateUrl: './employment-contract.component.html',
  styleUrl: './employment-contract.component.scss',
})
export class EmploymentContractComponent {
  readonly employeeId = input.required<string>();
  private readonly api = inject(EmploymentContractApi);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  private request?: Subscription;
  private key = '';
  readonly current = signal<EmploymentContract | null>(null);
  readonly history = signal<EmploymentContractPage | null>(null);
  readonly busy = signal(false);
  readonly message = signal('Consultando contrato…');
  readonly formatWorkDate = formatWorkDate;
  readonly form = new FormGroup({
    effectiveFrom: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    countryCode: new FormControl('BR', {
      nonNullable: true,
      validators: [Validators.required, Validators.pattern(/^[A-Z]{2}$/)],
    }),
    employmentType: new FormControl<'FULL_TIME' | 'PART_TIME'>('FULL_TIME', { nonNullable: true }),
    weeklyMinutes: new FormControl(2400, {
      nonNullable: true,
      validators: [Validators.required, Validators.min(1), Validators.max(10080)],
    }),
  });
  constructor() {
    this.destroy.onDestroy(() => this.request?.unsubscribe());
    effect(() => {
      const id = this.employeeId();
      untracked(() => {
        if (id === this.key) return;
        this.key = id;
        this.request?.unsubscribe();
        this.current.set(null);
        this.history.set(null);
        this.busy.set(false);
        this.message.set('Consulte o contrato vigente ou cadastre uma nova versão.');
      });
    });
  }
  loadCurrent(): void {
    this.request?.unsubscribe();
    this.busy.set(true);
    this.request = this.api.current(this.employeeId()).subscribe({
      next: (value) => {
        this.current.set(value);
        this.busy.set(false);
        this.message.set(value ? 'Contrato vigente carregado.' : 'Nenhum contrato cadastrado.');
      },
      error: (error) => this.fail(error),
    });
  }
  loadHistory(page = 0): void {
    if (this.busy() || page < 0) return;
    this.request?.unsubscribe();
    this.busy.set(true);
    this.request = this.api.history(this.employeeId(), page).subscribe({
      next: (value) => {
        this.history.set(value);
        this.busy.set(false);
        this.message.set(
          value.totalElements ? 'Histórico de contratos carregado.' : 'Nenhum contrato cadastrado.',
        );
      },
      error: (error) => this.fail(error),
    });
  }
  save(): void {
    if (this.busy()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.message.set('Preencha corretamente a vigência, o país e a carga semanal.');
      return;
    }
    this.busy.set(true);
    this.request = this.api.create(this.employeeId(), this.form.getRawValue()).subscribe({
      next: (value) => {
        this.current.set(value);
        this.history.set(null);
        this.busy.set(false);
        this.message.set('Nova versão do contrato cadastrada com sucesso.');
      },
      error: (error) => this.fail(error),
    });
  }
  hours(minutes: number): string {
    return `${Math.floor(minutes / 60)} h${minutes % 60 ? ` ${minutes % 60} min` : ''}`;
  }
  type(value: string): string {
    return value === 'FULL_TIME' ? 'Tempo integral' : 'Tempo parcial';
  }
  country(value: string): string {
    return (
      (
        {
          BR: 'Brasil',
          PT: 'Portugal',
          ES: 'Espanha',
          FR: 'França',
          DE: 'Alemanha',
          NL: 'Países Baixos',
        } as Record<string, string>
      )[value] ?? value
    );
  }
  private fail(error: unknown): void {
    this.busy.set(false);
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 401) {
      this.auth.invalidate();
      return;
    }
    this.message.set(
      status === 409
        ? 'Já existe um contrato com essa data de vigência.'
        : status === 403
          ? 'Seu acesso não permite administrar contratos.'
          : 'Não foi possível concluir a operação. Tente novamente.',
    );
  }
}
