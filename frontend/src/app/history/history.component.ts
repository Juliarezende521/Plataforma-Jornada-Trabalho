import { Component, computed, DestroyRef, ElementRef, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService, Identity } from '../auth/auth.service';
import { formatInstant, formatWorkDate } from '../journey/journey-format';
import { HistoryApi } from './history-api.service';
import { EmployeeSummary, HistoryFilter, HistoryPage, JourneyDetail } from './history.types';
@Component({ selector: 'app-history', imports: [ReactiveFormsModule], templateUrl: './history.component.html', styleUrl: './history.component.scss' })
export class HistoryComponent {
  readonly user = input.required<Identity>();
  readonly targetEmployee = input<EmployeeSummary | null>(null);
  readonly referenceZone = computed(() => this.targetEmployee()?.referenceTimeZoneId ?? this.user().referenceTimeZoneId);
  private readonly api = inject(HistoryApi);
  private readonly auth = inject(AuthService);
  private readonly destroyRef = inject(DestroyRef);
  private request?: Subscription;
  private identityKey = '';
  readonly busy = signal(false);
  readonly result = signal<HistoryPage | null>(null);
  readonly detail = signal<JourneyDetail | null>(null);
  readonly message = signal('Escolha o período para consultar suas jornadas.');
  readonly fieldError = signal('');
  readonly applied = signal<HistoryFilter | null>(null);
  readonly zone = new FormControl('reference', { nonNullable: true });
  readonly form = new FormGroup({ from: new FormControl('', { nonNullable: true }), to: new FormControl('', { nonNullable: true }) });
  readonly heading = viewChild<ElementRef<HTMLElement>>('historyHeading');
  readonly detailHeading = viewChild<ElementRef<HTMLElement>>('detailHeading');
  readonly formatWorkDate = formatWorkDate;
  readonly eventLabels = { ENTRY: 'Entrada', PAUSE_START: 'Início de pausa', PAUSE_END: 'Retorno', EXIT: 'Saída' };
  constructor() {
    this.destroyRef.onDestroy(() => this.request?.unsubscribe());
    effect(() => { const user=this.user(); const target=this.targetEmployee(); untracked(() => {
      const key=user.userId + ':' + user.employeeId + ':' + (target?.id ?? 'own');
      if (key===this.identityKey) return;
      this.identityKey=key; this.request?.unsubscribe(); this.result.set(null); this.detail.set(null); this.applied.set(null); this.busy.set(false);
      this.zone.setValue('reference'); this.fieldError.set(''); this.message.set('Escolha o período para consultar suas jornadas.');
      const today=new Intl.DateTimeFormat('en-CA', { timeZone: this.referenceZone(), year: 'numeric', month: '2-digit', day: '2-digit' }).format(new Date());
      this.form.setValue({ from: today.slice(0,8) + '01', to: today });
    }); });
  }
  focusHeading(): void { this.heading()?.nativeElement.focus(); }

  formatInstant(instant: string, reference: string): string { return formatInstant(instant, this.zone.value==='reference' ? this.referenceZone() : this.zone.value); }
  stateLabel(state: string): string { return state==='CLOSED' ? 'Encerrada' : state==='PAUSED' ? 'Em pausa · pendente' : 'Trabalhando · pendente'; }
  consult(): void {
    if (this.busy()) return;
    const filter=this.form.getRawValue();
    const day=(value: string) => /^\d{4}-\d{2}-\d{2}$/.test(value) && Number.isFinite(Date.parse(value)) && new Date(value).toISOString().slice(0,10)===value;
    const span=(Date.parse(filter.to)-Date.parse(filter.from))/86400000;
    if (!day(filter.from) || !day(filter.to) || span<0 || span>365) {
      this.fieldError.set('Informe datas válidas, com o fim igual ou posterior ao início e no máximo 366 dias.'); return;
    }
    this.fieldError.set(''); this.applied.set(filter); this.load(0);
  }
  load(page: number): void {
    const filter=this.applied(); if (!filter || this.busy() || page<0) return;
    this.busy.set(true); this.result.set(null); this.detail.set(null); this.message.set('Consultando jornadas do período…');
    this.request=this.api.history(filter,page,this.targetEmployee()?.id).subscribe({ next: result => { this.result.set(result); this.busy.set(false);
      this.message.set(result.totalElements ? 'Consulta concluída. Seus registros estão disponíveis.' : 'Nenhuma jornada encontrada no período selecionado.'); this.heading()?.nativeElement.focus(); },
      error: error => this.fail(error) });
  }
  open(id: string): void {
    if (this.busy()) return;
    this.busy.set(true); this.detail.set(null); this.message.set('Consultando os detalhes…');
    this.request=this.api.detail(id,this.targetEmployee()?.id).subscribe({ next: detail => { this.detail.set(detail); this.busy.set(false); this.message.set('Detalhes consultados.');
      setTimeout(() => { if (!this.destroyRef.destroyed) this.detailHeading()?.nativeElement.focus(); }); }, error: error => this.fail(error) });
  }
  back(): void { this.detail.set(null); this.message.set('Filtros e página preservados.'); this.heading()?.nativeElement.focus(); }
  private fail(error: unknown): void {
    this.busy.set(false);
    const status=error instanceof HttpErrorResponse ? error.status : 0;
    if (status===401) { this.auth.invalidate(); return; }
    this.message.set(status===403 ? 'Seu acesso a estas jornadas não está disponível. Verifique seu acesso.' : status===404 ? 'Esta jornada não está disponível para sua conta. Volte ao histórico e consulte novamente.' : status===400 ? 'O período ou a página não é válido. Revise os filtros.' : 'Não foi possível concluir a consulta. Seus filtros foram preservados; tente consultar novamente.');
  }
}
