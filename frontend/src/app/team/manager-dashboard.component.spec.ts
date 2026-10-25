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

  afterEach(() => http.verify());

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
});
