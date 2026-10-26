import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ManagerDashboardComponent } from './manager-dashboard.component';

describe('ManagerDashboardComponent', () => {
  let fixture: ComponentFixture<ManagerDashboardComponent>;
  let http: HttpTestingController;
  let view: HTMLElement;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [ManagerDashboardComponent], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(ManagerDashboardComponent);
    fixture.componentRef.setInput('user', { userId: 'u', employeeId: 'm', name: 'Gestora', email: 'gestora@example.invalid', referenceTimeZoneId: 'UTC', manager: true });
    view = fixture.nativeElement;
    fixture.detectChanges();
  });

  afterEach(() => { fixture.destroy(); http.verify(); vi.restoreAllMocks(); });

  it('lists only the server-provided team and opens manager-scoped history', () => {
    http.expectOne('/api/manager/team?page=0&size=20').flush({ items: [{ id: 'e', name: 'Colaborador', referenceTimeZoneId: 'America/Sao_Paulo' }], page: 0, size: 20, totalElements: 1 });
    fixture.detectChanges();
    expect(view.textContent).toContain('Colaborador');
    view.querySelector<HTMLButtonElement>('button[aria-label="Ver jornadas de Colaborador"]')!.click();
    fixture.detectChanges();
    expect(view.querySelector('app-history')).not.toBeNull();
    expect(view.textContent).toContain('Colaborador:');
    expect(view.textContent).not.toContain('Contrato vigente');
  });

  it('opens and decides an adjustment through manager-scoped endpoints', () => {
    http.expectOne('/api/manager/team?page=0&size=20').flush({ items: [], page: 0, size: 20, totalElements: 0 });
    fixture.detectChanges();
    const button = [...view.querySelectorAll<HTMLButtonElement>('button')].find(item => item.textContent?.includes('Ajustes pendentes'))!;
    button.click(); fixture.detectChanges();
    const item = { id: '00000000-0000-0000-0000-000000000020', workSessionId: 's', employeeId: 'e', employeeName: 'Colaboradora', requestedEventType: 'EXIT', proposedOccurredAtUtc: '2026-10-19T21:00:00Z', reason: 'Saída não registrada no horário.', status: 'PENDING', requestedAtUtc: '2026-10-20T12:00:00Z', decidedAtUtc: null, decisionNote: null };
    http.expectOne('/api/manager/work-adjustments?page=0&size=20').flush({ items: [item], page: 0, size: 20, totalElements: 1 });
    fixture.detectChanges();
    expect(view.textContent).toContain('Ajustes da equipe');
    expect(view.textContent).toContain('subordinados diretos');
    [...view.querySelectorAll<HTMLButtonElement>('button')].find(candidate => candidate.textContent?.includes('Analisar'))!.click();
    fixture.detectChanges(); vi.spyOn(window, 'confirm').mockReturnValue(true);
    [...view.querySelectorAll<HTMLButtonElement>('button')].find(candidate => candidate.textContent?.includes('Rejeitar'))!.click();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'token' });
    const decision = http.expectOne('/api/manager/work-adjustments/00000000-0000-0000-0000-000000000020/decision');
    expect(decision.request.body).toEqual({ decision: 'REJECTED', note: null });
    decision.flush({ ...item, status: 'REJECTED', decidedAtUtc: '2026-10-25T18:00:00Z' });
    http.expectOne('/api/manager/work-adjustments?page=0&size=20').flush({ items: [], page: 0, size: 20, totalElements: 0 });
  });
});
