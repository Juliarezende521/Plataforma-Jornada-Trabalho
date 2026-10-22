import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { formatWorkDate } from '../journey/journey-format';
import { WorkScheduleApi } from './work-schedule-api.service';
import { WorkSchedule, WorkScheduleInput } from './work-schedule.types';

@Component({
  selector: 'app-work-schedule', imports: [ReactiveFormsModule],
  templateUrl: './work-schedule.component.html', styleUrl: './work-schedule.component.scss',
})
export class WorkScheduleComponent {
  readonly employeeId = input<string | null>(null);
  readonly editable = input(false);
  private readonly api = inject(WorkScheduleApi);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  private request?: Subscription;
  private key = '';
  readonly schedule = signal<WorkSchedule | null>(null);
  readonly busy = signal(false);
  readonly message = signal('Consultando escala…');
  readonly days = ['Segunda', 'Terça', 'Quarta', 'Quinta', 'Sexta', 'Sábado', 'Domingo'];
  readonly dayControls = ['monday', 'tuesday', 'wednesday', 'thursday', 'friday', 'saturday', 'sunday'] as const;
  readonly formatWorkDate = formatWorkDate;
  readonly form = new FormGroup({
    effectiveFrom: new FormControl('', { nonNullable: true, validators: [Validators.required] }),
    monday: new FormControl('08:00', { nonNullable: true, validators: [Validators.required] }),
    tuesday: new FormControl('08:00', { nonNullable: true, validators: [Validators.required] }),
    wednesday: new FormControl('08:00', { nonNullable: true, validators: [Validators.required] }),
    thursday: new FormControl('08:00', { nonNullable: true, validators: [Validators.required] }),
    friday: new FormControl('08:00', { nonNullable: true, validators: [Validators.required] }),
    saturday: new FormControl('00:00', { nonNullable: true, validators: [Validators.required] }),
    sunday: new FormControl('00:00', { nonNullable: true, validators: [Validators.required] }),
  });

  constructor() {
    this.destroy.onDestroy(() => this.request?.unsubscribe());
    effect(() => {
      const id = this.employeeId();
      untracked(() => {
        const key = id ?? 'mine';
        if (key === this.key) return;
        this.key = key;
        this.load();
      });
    });
  }

  load(): void {
    this.request?.unsubscribe();
    this.busy.set(true);
    this.schedule.set(null);
    this.message.set('Consultando escala…');
    this.request = this.api.get(this.employeeId() ?? undefined).subscribe({
      next: value => {
        this.schedule.set(value);
        this.busy.set(false);
        this.message.set(value ? 'Escala vigente carregada.' : 'Nenhuma escala cadastrada.');
      },
      error: error => this.fail(error),
    });
  }

  save(): void {
    const employee = this.employeeId();
    if (!this.editable() || !employee || this.busy()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) {
      this.message.set('Preencha a data de vigência e todos os dias da semana.');
      return;
    }
    const value = this.form.getRawValue();
    const times = [value.monday, value.tuesday, value.wednesday, value.thursday, value.friday, value.saturday, value.sunday];
    const minutes = times.map(time => this.toMinutes(time));
    if (minutes.some(item => item === null) || minutes.every(item => item === 0)) {
      this.message.set('Informe pelo menos um dia com carga planejada válida.');
      return;
    }
    const input: WorkScheduleInput = {
      effectiveFrom: value.effectiveFrom,
      mondayMinutes: minutes[0]!, tuesdayMinutes: minutes[1]!, wednesdayMinutes: minutes[2]!,
      thursdayMinutes: minutes[3]!, fridayMinutes: minutes[4]!, saturdayMinutes: minutes[5]!, sundayMinutes: minutes[6]!,
    };
    this.busy.set(true);
    this.request = this.api.create(employee, input).subscribe({
      next: schedule => {
        this.schedule.set(schedule);
        this.busy.set(false);
        this.message.set('Nova escala cadastrada com sucesso.');
      },
      error: error => this.fail(error),
    });
  }

  format(minutes: number): string {
    return `${Math.floor(minutes / 60)} h${minutes % 60 ? ` ${minutes % 60} min` : ''}`;
  }

  private toMinutes(value: string): number | null {
    if (!/^\d{2}:\d{2}$/.test(value)) return null;
    const [hour, minute] = value.split(':').map(Number);
    return hour <= 23 && minute <= 59 ? hour * 60 + minute : null;
  }

  private fail(error: unknown): void {
    this.busy.set(false);
    const status = error instanceof HttpErrorResponse ? error.status : 0;
    if (status === 401) {
      this.auth.invalidate();
      return;
    }
    this.message.set(status === 409
      ? 'Já existe uma escala com essa data de vigência.'
      : 'Não foi possível concluir a operação. Tente novamente.');
  }
}
