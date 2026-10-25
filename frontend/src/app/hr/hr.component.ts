import { Component, DestroyRef, ElementRef, effect, inject, input, signal, untracked, viewChild } from '@angular/core';
import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { FormControl, ReactiveFormsModule } from '@angular/forms';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { Subscription, timeout } from 'rxjs';
import { AuthService, Identity } from '../auth/auth.service';
import { HistoryComponent } from '../history/history.component';
import { EmployeePage, EmployeeSummary } from '../history/history.types';
import { WorkScheduleComponent } from '../schedule/work-schedule.component';
import { TeamAssignmentComponent } from '../team/team-assignment.component';

@Component({ selector: 'app-hr', imports: [ReactiveFormsModule, HistoryComponent, WorkScheduleComponent, TeamAssignmentComponent],
  templateUrl: './hr.component.html', styleUrl: './hr.component.scss' })
export class HrComponent {
  readonly user = input.required<Identity>();
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  private readonly heading = viewChild<ElementRef<HTMLElement>>('heading');
  private request?: Subscription;
  readonly choice = new FormControl('', { nonNullable: true });
  readonly directory = signal<EmployeePage | null>(null);
  readonly selected = signal<EmployeeSummary | null>(null);
  readonly busy = signal(false);
  readonly message = signal('');
  constructor() {
    this.destroy.onDestroy(() => this.request?.unsubscribe());
    effect(() => { const user = this.user(); untracked(() => {
      this.request?.unsubscribe(); this.busy.set(false); this.directory.set(null); this.clearSelection();
      if (user.hrReader) this.load(0);
    }); });
    this.choice.valueChanges.pipe(takeUntilDestroyed(this.destroy)).subscribe(id => {
      this.selected.set(this.directory()?.items.find(person => person.id === id) ?? null);
    });
  }
  focusHeading(): void { this.heading()?.nativeElement.focus(); }
  private clearSelection(): void { this.selected.set(null); this.choice.setValue('', { emitEvent: false }); }
  load(page: number): void {
    if (this.busy() || page < 0 || !this.user().hrReader) return;
    this.clearSelection(); this.directory.set(null); this.busy.set(true); this.message.set('Consultando colaboradores…');
    this.request = this.http.get<EmployeePage>('/api/hr/employees', { params: { page, size: 20 } }).pipe(timeout(15000)).subscribe({
      next: result => { this.directory.set(result); this.busy.set(false);
        this.message.set(result.items.length ? 'Selecione um colaborador para consultar seus registros.' : 'Nenhum colaborador disponível.'); },
      error: (error: unknown) => { this.busy.set(false); this.directory.set(null); this.clearSelection();
        if (error instanceof HttpErrorResponse && error.status === 401) this.auth.invalidate();
        this.message.set(error instanceof HttpErrorResponse && error.status === 403
          ? 'Seu acesso de RH não está disponível. Verifique seu acesso.' : 'Não foi possível consultar colaboradores. Tente novamente.'); },
    });
  }
}
