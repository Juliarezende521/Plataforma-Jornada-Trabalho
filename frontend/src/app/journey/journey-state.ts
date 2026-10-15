import { DestroyRef, inject, Injectable, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { HttpErrorResponse } from '@angular/common/http';
import { firstValueFrom, Observable } from 'rxjs';
import { AuthService, Identity } from '../auth/auth.service';
import { AttemptDiary } from './attempt-diary';
import { JourneyApi } from './journey-api.service';
import { ACTION_LABELS, Attempt, CurrentJourney, EventType, WorkMode, Receipt, sameIdentity } from './journey.types';

@Injectable()
export class JourneyState {
  private readonly auth = inject(AuthService);
  private readonly api = inject(JourneyApi);
  private readonly diary = inject(AttemptDiary);
  private readonly destroy = inject(DestroyRef);
  private identity!: Identity;
  private alive = true;
  private changedDuringRequest = false;
  private focusRequested = false;
  readonly focusCompletion = signal(0);
  readonly current = signal<CurrentJourney | null>(null);
  readonly pending = signal<Attempt[]>([]);
  readonly busy = signal(false);
  readonly ready = signal(false);
  readonly message = signal('Consultando sua jornada…');
  readonly lastReceipt = signal<Receipt | null>(null);

  constructor() {
    const onStorage = (event: StorageEvent) => {
      if (!this.diary.isDiaryEvent(event)) return;
      this.ready.set(false);
      if (this.busy()) this.changedDuringRequest = true;
      else void this.refresh(false);
    };
    window.addEventListener('storage', onStorage);
    this.destroy.onDestroy(() => { this.alive = false; window.removeEventListener('storage', onStorage); });
  }

  connect(identity: Identity): void {
    if (this.identity) {
      if (!sameIdentity(identity, this.identity)) {
        this.ready.set(false);
        this.auth.invalidate();
      }
      return;
    }
    this.identity = identity;
    void this.refresh(false);
  }

  async refresh(requestFocus = true): Promise<void> {
    if (this.busy()) return;
    this.begin('Atualizando jornada e verificando marcações…', requestFocus);
    try {
      if (!await this.confirmIdentity()) return;
      await this.reconcile();
      await this.loadCurrent();
      if (!this.pending().length) this.message.set('Jornada atualizada. Os horários exibidos vêm do servidor.');
      else this.message.set('Há uma marcação sem confirmação. Consulte o resultado antes de continuar.');
    } catch (error) { this.fail(error); }
    finally { this.finish(); }
  }

  async mark(type: EventType, workMode?: WorkMode): Promise<void> {
    if (this.busy() || !this.ready() || this.pending().length) return;
    const expected = this.current()?.session?.id ?? null;
    if (!this.current()?.allowedActions.includes(type)) return;
    this.begin('Conferindo sua jornada antes de registrar…');
    try {
      if (!await this.confirmIdentity()) return;
      await this.reconcile();
      await this.loadCurrent();
      if (this.pending().length || !this.current()?.allowedActions.includes(type) ||
          (type !== 'ENTRY' && this.current()?.session?.id !== expected)) {
        this.message.set('A jornada mudou ou existe marcação pendente. Confira o estado atualizado antes de registrar.');
        return;
      }
      const attempt = this.diary.save(this.identity, { type, expectedWorkSessionId: type === 'ENTRY' ? null : expected,
        timeZoneId: Intl.DateTimeFormat().resolvedOptions().timeZone, ...(workMode ? { workMode } : {}) });
      this.pending.set(this.diary.list(this.identity));
      if (this.pending().some(item => item.requestKey !== attempt.requestKey)) {
        this.message.set('Outra aba também iniciou uma marcação. Consulte os resultados antes de continuar.');
        return;
      }
      await this.send(attempt);
    } catch (error) { this.fail(error); }
    finally { this.finish(); }
  }

  async retry(attempt: Attempt): Promise<void> {
    if (this.busy() || !this.pending().some(item => item.requestKey === attempt.requestKey)) return;
    this.begin('Consultando a tentativa antes de repetir…');
    try {
      if (!await this.confirmIdentity()) return;
      const receipt = await this.request(this.api.receipt(attempt.requestKey));
      if (receipt !== null) { this.accept(attempt, receipt); await this.loadCurrent(); return; }
      // Explicit retry only. No replay after a timeout, reload or storage event.
      if (!this.diary.list(this.identity).some(item => JSON.stringify(item) === JSON.stringify(attempt))) {
        throw new Error('Attempt changed');
      }
      await this.send(attempt);
    } catch (error) { this.fail(error); }
    finally { this.finish(); }
  }

  private async send(attempt: Attempt): Promise<void> {
    this.message.set('Registrando… aguarde a confirmação.');
    const receipt = await this.request(this.api.mark(attempt));
    this.accept(attempt, receipt);
    await this.loadCurrent();
  }

  private async reconcile(): Promise<void> {
    this.pending.set(this.diary.list(this.identity));
    for (const attempt of this.pending()) {
      const receipt = await this.request(this.api.receipt(attempt.requestKey));
      if (receipt !== null) this.accept(attempt, receipt);
    }
    this.pending.set(this.diary.list(this.identity));
  }

  private accept(attempt: Attempt, receipt: Receipt): void {
    if (!this.api.isReceipt(receipt, attempt)) throw new Error('Unexpected receipt');
    if (receipt.code === 'KEY_REUSED') {
      this.message.set('Esta tentativa não corresponde ao resultado original. Consulte o resultado; não crie outra marcação para substituir esta chave.');
      return;
    }
    this.lastReceipt.set(receipt);
    this.diary.remove(attempt);
    this.pending.set(this.diary.list(this.identity));
    this.message.set(receipt.httpStatus === 201 ? `${ACTION_LABELS[attempt.type]}: confirmado pelo servidor.` :
      this.rejectionMessage(receipt.code));
  }

  private rejectionMessage(code: string): string {
    const messages: Record<string, string> = {
      ALREADY_OPEN: 'Já existe uma jornada aberta. Confira o estado atualizado.',
      NO_OPEN_SESSION: 'Não existe jornada aberta para receber essa marcação.',
      TARGET_CHANGED: 'A jornada mudou. Esta tentativa não foi aplicada a outra jornada.',
      INVALID_TRANSITION: 'Esta ação não é permitida no estado atual. Confira a jornada atualizada.',
      CLOCK_REGRESSION: 'O horário da plataforma precisa ser verificado. Esta tentativa foi recusada.',
    };
    return messages[code] ?? 'A marcação foi recusada. Confira a jornada atualizada.';
  }

  private async confirmIdentity(): Promise<boolean> {
    const identity = await this.request(this.auth.refresh());
    if (!sameIdentity(identity, this.identity)) {
      this.ready.set(false);
      this.message.set('Seu acesso mudou. Entre novamente antes de continuar.');
      return false;
    }
    return true;
  }

  private async loadCurrent(): Promise<void> {
    this.current.set(await this.request(this.api.current()));
    this.ready.set(true);
  }

  private request<T>(request: Observable<T>): Promise<T> {
    return firstValueFrom(request.pipe(takeUntilDestroyed(this.destroy)));
  }

  private begin(message: string, requestFocus = true): void {
    this.focusRequested = requestFocus;
    this.busy.set(true); this.ready.set(false); this.message.set(message);
  }

  private fail(error: unknown): void {
    this.ready.set(false);
    if (!this.alive) return;
    if (error instanceof HttpErrorResponse && error.status === 401) {
      this.auth.invalidate();
      return;
    }
    if (error instanceof HttpErrorResponse && error.status === 403) {
      this.message.set('Seu acesso precisa ser atualizado. Use Atualizar jornada; se persistir, entre novamente.');
    } else {
      this.message.set(this.pending().length ?
        'Não foi possível confirmar o resultado. Sua tentativa foi preservada. Use Consultar resultados antes de repetir.' :
        'Não foi possível atualizar ou preservar sua tentativa. Confira a conexão e permita o armazenamento do navegador antes de tentar novamente.');
    }
  }

  private finish(): void {
    this.busy.set(false);
    if (this.alive && this.focusRequested) this.focusCompletion.update(value => value + 1);
    if (this.alive && this.changedDuringRequest) {
      this.changedDuringRequest = false;
      this.ready.set(false);
      void this.refresh(false);
    }
  }
}
