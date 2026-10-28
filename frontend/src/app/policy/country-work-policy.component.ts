import { Component, DestroyRef, ElementRef, inject, signal, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { formatWorkDate } from '../journey/journey-format';
import { CountryWorkPolicyApi } from './country-work-policy-api.service';
import { CountryWorkPolicy, CountryWorkPolicyPage } from './country-work-policy.types';

@Component({ selector: 'app-country-work-policy', imports: [ReactiveFormsModule],
  templateUrl: './country-work-policy.component.html', styleUrl: './country-work-policy.component.scss' })
export class CountryWorkPolicyComponent {
  private readonly api = inject(CountryWorkPolicyApi);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private request?: Subscription;
  readonly current = signal<CountryWorkPolicy | null>(null);
  readonly history = signal<CountryWorkPolicyPage | null>(null);
  readonly busy = signal(false);
  readonly message = signal('Selecione o país para consultar ou cadastrar uma política.');
  readonly formatWorkDate = formatWorkDate;
  readonly form = new FormGroup({
    countryCode: new FormControl('BR', { nonNullable: true, validators: [Validators.required, Validators.pattern(/^[A-Z]{2}$/)] }),
    effectiveFrom: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    standardWeeklyMinutes: new FormControl(2400, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(10080)] }),
    maximumDailyMinutes: new FormControl(600, { nonNullable: true, validators: [Validators.required, Validators.min(1), Validators.max(1440)] }),
    minimumBreakMinutes: new FormControl(60, { nonNullable: true, validators: [Validators.required, Validators.min(0), Validators.max(1440)] }),
    overtimeToleranceMinutes: new FormControl(10, { nonNullable: true, validators: [Validators.required, Validators.min(0), Validators.max(1440)] }),
  });
  constructor() { this.destroy.onDestroy(() => this.request?.unsubscribe()); }
  focusHeading(): void { this.heading()?.nativeElement.focus(); }
  loadCurrent(): void { this.start(this.api.current(this.form.controls.countryCode.value, today()).subscribe({
    next: value => { this.current.set(value); this.busy.set(false); this.message.set(value ? 'Política vigente carregada.' : 'Nenhuma política vigente para o país.'); },
    error: error => this.fail(error),
  })); }
  loadHistory(page = 0): void {
    if (page < 0) return;
    this.start(this.api.history(this.form.controls.countryCode.value, page).subscribe({
      next: value => { this.history.set(value); this.busy.set(false); this.message.set(value.totalElements ? 'Histórico carregado.' : 'Nenhuma política cadastrada.'); },
      error: error => this.fail(error),
    }));
  }
  save(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.message.set('Revise a vigência e os limites informados.'); return; }
    this.start(this.api.create(this.form.getRawValue()).subscribe({
      next: value => { this.current.set(value); this.history.set(null); this.busy.set(false); this.message.set('Nova versão da política cadastrada.'); },
      error: error => this.fail(error),
    }));
  }
  hours(minutes: number): string { return `${Math.floor(minutes / 60)} h${minutes % 60 ? ` ${minutes % 60} min` : ''}`; }
  country(code: string): string { return ({ BR: 'Brasil', PT: 'Portugal', ES: 'Espanha', FR: 'França', DE: 'Alemanha', NL: 'Países Baixos' } as Record<string,string>)[code] ?? code; }
  private start(request: Subscription): void { if (this.busy()) { request.unsubscribe(); return; } this.request?.unsubscribe(); this.busy.set(true); this.request = request; }
  private fail(error: unknown): void {
    this.busy.set(false); const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 401) { this.auth.invalidate(); return; }
    this.message.set(status === 409 ? 'Já existe uma política com essa vigência para o país.'
      : status === 403 ? 'Seu acesso não permite administrar políticas.' : 'Não foi possível concluir a operação. Tente novamente.');
  }
}

function today(): string {
  const date = new Date();
  return `${date.getFullYear().toString().padStart(4, '0')}-${(date.getMonth() + 1).toString().padStart(2, '0')}-${date.getDate().toString().padStart(2, '0')}`;
}
