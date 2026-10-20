import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { EventType } from '../journey/journey.types';
import { formatInstant } from '../journey/journey-format';
import { AdjustmentApi } from './adjustment-api.service';
import { WorkAdjustment } from './adjustment.types';

@Component({ selector: 'app-adjustment-panel', imports: [ReactiveFormsModule],
  templateUrl: './adjustment-panel.component.html', styleUrl: './adjustment-panel.component.scss' })
export class AdjustmentPanelComponent {
  readonly workSessionId=input.required<string>();
  readonly displayZone=input.required<string>();
  readonly items=signal<WorkAdjustment[]>([]);
  readonly busy=signal(false);
  readonly message=signal('');
  readonly form=new FormGroup({
    requestedEventType: new FormControl<EventType>('ENTRY', { nonNullable: true, validators: Validators.required }),
    proposedLocalTime: new FormControl('', { nonNullable: true, validators: Validators.required }),
    reason: new FormControl('', { nonNullable: true, validators: [Validators.required, Validators.minLength(10), Validators.maxLength(500)] }),
  });
  readonly labels: Record<EventType,string>={ ENTRY:'Entrada', PAUSE_START:'Início de pausa', PAUSE_END:'Retorno', EXIT:'Saída' };
  readonly eventTypes: EventType[]=['ENTRY','PAUSE_START','PAUSE_END','EXIT'];
  private readonly api=inject(AdjustmentApi);
  private readonly auth=inject(AuthService);
  private readonly destroy=inject(DestroyRef);
  private request?: Subscription;
  constructor() {
    this.destroy.onDestroy(() => this.request?.unsubscribe());
    effect(() => { const session=this.workSessionId(); untracked(() => this.load(session)); });
  }
  submit(): void {
    this.form.markAllAsTouched();
    if (this.form.invalid || this.busy()) return;
    const value=this.form.getRawValue();
    const proposed=new Date(value.proposedLocalTime);
    if (!Number.isFinite(proposed.getTime()) || proposed.getTime()>Date.now()) {
      this.message.set('Informe um horário válido que não esteja no futuro.'); return;
    }
    this.busy.set(true); this.message.set('Enviando solicitação…');
    this.request=this.api.request({ workSessionId:this.workSessionId(), requestedEventType:value.requestedEventType,
      proposedOccurredAtUtc:proposed.toISOString(), reason:value.reason.trim() }).subscribe({
      next: item => { this.items.update(items => [item,...items]); this.busy.set(false); this.form.controls.reason.reset('');
        this.message.set('Solicitação enviada ao RH. As marcações originais foram preservadas.'); },
      error: error => this.fail(error),
    });
  }
  format(instant: string): string { return formatInstant(instant,this.displayZone()); }
  status(value: string): string { return value==='PENDING' ? 'Aguardando análise' : value==='APPROVED' ? 'Aprovada' : 'Rejeitada'; }
  private load(session: string): void {
    this.request?.unsubscribe(); this.busy.set(true); this.message.set('Consultando solicitações…');
    this.request=this.api.mine(session).subscribe({
      next: page => { this.items.set(page.items); this.busy.set(false); this.message.set(page.items.length ? '' : 'Nenhuma correção solicitada para esta jornada.'); },
      error: error => this.fail(error),
    });
  }
  private fail(error: unknown): void {
    this.busy.set(false);
    if (error instanceof HttpErrorResponse && error.status===401) { this.auth.invalidate(); return; }
    this.message.set(error instanceof HttpErrorResponse && error.status===400 ? 'Revise o evento, o horário e a justificativa.' :
      error instanceof HttpErrorResponse && error.status===404 ? 'Esta jornada não está mais disponível.' : 'Não foi possível concluir a solicitação. Tente novamente.');
  }
}
