import { Component, DestroyRef, ElementRef, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { FormControl, ReactiveFormsModule, Validators } from '@angular/forms';
import { HttpErrorResponse } from '@angular/common/http';
import { Subscription } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { formatInstant } from '../journey/journey-format';
import { EventType } from '../journey/journey.types';
import { AdjustmentApi } from './adjustment-api.service';
import { AdjustmentPage, WorkAdjustment } from './adjustment.types';

@Component({ selector:'app-adjustment-review', imports:[ReactiveFormsModule],
  templateUrl:'./adjustment-review.component.html', styleUrl:'./adjustment-review.component.scss' })
export class AdjustmentReviewComponent {
  readonly access=input<'hr'|'manager'>('hr');
  readonly page=signal<AdjustmentPage|null>(null);
  readonly selected=signal<WorkAdjustment|null>(null);
  readonly busy=signal(false);
  readonly message=signal('');
  readonly note=new FormControl('', { nonNullable:true, validators:[Validators.maxLength(500)] });
  readonly labels:Record<EventType,string>={ ENTRY:'Entrada', PAUSE_START:'Início de pausa', PAUSE_END:'Retorno', EXIT:'Saída' };
  private readonly api=inject(AdjustmentApi);
  private readonly auth=inject(AuthService);
  private readonly destroy=inject(DestroyRef);
  private readonly heading=viewChild<ElementRef<HTMLElement>>('heading');
  private request?:Subscription;
  constructor() {
    this.destroy.onDestroy(() => this.request?.unsubscribe());
    effect(() => { this.access(); untracked(() => { this.request?.unsubscribe(); this.busy.set(false); this.load(0); }); });
  }
  focusHeading():void { this.heading()?.nativeElement.focus(); }
  load(page:number):void {
    if(this.busy()||page<0)return;
    this.busy.set(true); this.message.set('Consultando solicitações pendentes…');
    this.request=this.api.pending(page,this.access()).subscribe({next:result=>{this.page.set(result);this.busy.set(false);this.selected.set(null);
      this.message.set(result.totalElements ? 'Solicitações pendentes disponíveis para análise.' : 'Não há solicitações pendentes.');},error:error=>this.fail(error)});
  }
  analyze(item:WorkAdjustment):void { this.selected.set(item); this.note.reset(''); this.message.set('Revise os dados antes de decidir.'); }
  cancel():void { this.selected.set(null); this.note.reset(''); }
  decide(decision:'APPROVED'|'REJECTED'):void {
    const item=this.selected(); this.note.markAsTouched();
    if(!item||this.note.invalid||this.busy())return;
    const verb=decision==='APPROVED'?'aprovar':'rejeitar';
    if(!window.confirm(`Confirma ${verb} esta solicitação? A decisão ficará registrada na auditoria.`))return;
    this.busy.set(true); this.message.set('Registrando decisão…');
    this.request=this.api.decide(item.id,decision,this.note.value.trim()||null,this.access()).subscribe({next:()=>{
      this.busy.set(false);this.selected.set(null);this.note.reset('');this.message.set(decision==='APPROVED'?'Solicitação aprovada e auditada.':'Solicitação rejeitada e auditada.');
      this.load(this.page()?.page??0);},error:error=>this.fail(error)});
  }
  format(value:string):string { return formatInstant(value,'America/Sao_Paulo'); }
  private fail(error:unknown):void {
    this.busy.set(false);
    if(error instanceof HttpErrorResponse&&error.status===401){this.auth.invalidate();return;}
    this.message.set(error instanceof HttpErrorResponse&&error.status===409?'A decisão conflita com a jornada atual ou a solicitação já foi analisada. Atualize a lista.':
      error instanceof HttpErrorResponse&&error.status===403?'Seu acesso não permite analisar ajustes.':'Não foi possível concluir a operação. Tente novamente.');
  }
}
