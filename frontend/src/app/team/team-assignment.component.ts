import { HttpClient, HttpErrorResponse } from '@angular/common/http';
import { Component, DestroyRef, effect, inject, input, signal, untracked } from '@angular/core';
import { FormControl, FormGroup, ReactiveFormsModule, Validators } from '@angular/forms';
import { Subscription, switchMap, timeout } from 'rxjs';
import { AuthService } from '../auth/auth.service';
import { EmployeePage, EmployeeSummary } from '../history/history.types';

interface Assignment { id:string; employeeId:string; managerEmployeeId:string; managerName:string; effectiveFrom:string; createdAtUtc:string; }

@Component({ selector: 'app-team-assignment', imports: [ReactiveFormsModule], templateUrl: './team-assignment.component.html', styleUrl: './team-assignment.component.scss' })
export class TeamAssignmentComponent {
  readonly employee = input.required<EmployeeSummary>();
  readonly current = signal<Assignment | null>(null);
  readonly candidates = signal<EmployeeSummary[]>([]);
  readonly busy = signal(false);
  readonly message = signal('');
  readonly form = new FormGroup({
    managerEmployeeId: new FormControl('', { nonNullable: true, validators: Validators.required }),
    effectiveFrom: new FormControl('', { nonNullable: true, validators: Validators.required }),
  });
  private readonly http = inject(HttpClient);
  private readonly auth = inject(AuthService);
  private readonly destroy = inject(DestroyRef);
  private request?: Subscription;

  constructor() {
    this.destroy.onDestroy(() => this.request?.unsubscribe());
    effect(() => { const employee = this.employee(); untracked(() => this.load(employee)); });
  }

  save(): void {
    if (this.busy()) return;
    this.form.markAllAsTouched();
    if (this.form.invalid) { this.message.set('Selecione o gestor e informe a data de vigência.'); return; }
    this.busy.set(true); this.message.set('Salvando vínculo…');
    const employee = this.employee();
    this.request = this.auth.csrf().pipe(switchMap(csrf => this.http.post<Assignment>(
      `/api/hr/employees/${encodeURIComponent(employee.id)}/manager-assignments`, this.form.getRawValue(),
      { headers: { [csrf.headerName]: csrf.token } }).pipe(timeout(15000)))).subscribe({
      next: assignment => { this.current.set(assignment); this.busy.set(false); this.message.set('Nova vigência de gestor registrada e auditada.'); },
      error: error => this.fail(error),
    });
  }

  private load(employee: EmployeeSummary): void {
    this.request?.unsubscribe(); this.current.set(null); this.candidates.set([]); this.busy.set(true); this.message.set('Consultando vínculo de equipe…');
    this.request = this.http.get<EmployeePage>('/api/hr/employees', { params: { page: 0, size: 100 } }).pipe(timeout(15000)).subscribe({
      next: page => {
        this.candidates.set(page.items.filter(person => person.id !== employee.id));
        this.request = this.http.get<Assignment | null>(`/api/hr/employees/${encodeURIComponent(employee.id)}/manager-assignment`).pipe(timeout(15000)).subscribe({
          next: assignment => { this.current.set(assignment); this.busy.set(false); this.message.set(assignment ? 'Gestor vigente consultado.' : 'Este colaborador ainda não possui gestor vigente.'); },
          error: error => this.fail(error),
        });
      },
      error: error => this.fail(error),
    });
  }

  private fail(error: unknown): void {
    this.busy.set(false);
    if (error instanceof HttpErrorResponse && error.status === 401) this.auth.invalidate();
    this.message.set(error instanceof HttpErrorResponse && error.status === 409
      ? 'O vínculo conflita com uma vigência existente ou criaria um ciclo.' : 'Não foi possível concluir a operação. Tente novamente.');
  }
}
