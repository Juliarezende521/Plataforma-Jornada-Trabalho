import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { HrDashboardComponent } from './hr-dashboard.component';
const hr = { userId: 'hr', employeeId: 'he', name: 'RH', email: 'rh@example.invalid', referenceTimeZoneId: 'UTC', hrReader: true };
const person = { id: 'b', name: 'Colaborador Brasil', referenceTimeZoneId: 'America/Sao_Paulo', state: 'WORKING' as const, workDate: '2026-10-14' };
describe('HR overview and separate individual consultation', () => {
  let fixture: ComponentFixture<HrDashboardComponent>; let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HrDashboardComponent], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController); fixture = TestBed.createComponent(HrDashboardComponent);
    fixture.componentRef.setInput('user', hr); fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); http.verify(); });
  function loaded() {
    http.expectOne('/api/hr/employees/overview?page=0&size=20').flush({ items: [person, { ...person, id: 'p', name: 'Pausa', state: 'PAUSED' }, { ...person, id: 'c', name: 'Fechada', state: 'CLOSED' }, { ...person, id: 'n', name: 'Nova', state: 'NOT_STARTED', workDate: null }], page: 0, size: 20, totalElements: 4, observedAtUtc: '2026-10-14T14:00:00Z' });
    fixture.detectChanges();
  }
  it('shows all four real states and keeps individual consultation as a separate screen', () => {
    loaded(); const view = fixture.nativeElement as HTMLElement;
    for (const label of ['Trabalhando', 'Em pausa', 'Encerrada', 'Sem jornada iniciada']) expect(view.textContent).toContain(label);
    expect(view.querySelector('app-hr')).toBeNull();
    fixture.componentInstance.show('individual'); fixture.detectChanges();
    http.expectOne('/api/hr/employees?page=0&size=20').flush({ items: [], page: 0, size: 20, totalElements: 0 });
    fixture.detectChanges(); expect(view.querySelector('app-hr')).not.toBeNull(); expect(view.querySelector('.overview')).toBeNull();
    fixture.componentInstance.show('overview'); fixture.detectChanges(); expect(view.querySelectorAll('.people li').length).toBe(4);
  });
  it('opens the selected employee history without loading another identity and clears it on return', () => {
    loaded(); fixture.componentInstance.open(person); fixture.detectChanges();
    const view = fixture.nativeElement as HTMLElement;
    expect(view.textContent).toContain('Jornadas de Colaborador Brasil');
    expect(view.querySelector('app-history')).not.toBeNull();
    http.expectNone('/api/me/work-sessions');
    fixture.componentInstance.show('overview'); fixture.detectChanges();
    expect(fixture.componentInstance.selected()).toBeNull(); expect(view.querySelector('app-history')).toBeNull();
  });
  it('removes stale states when refresh fails', () => {
    loaded(); fixture.componentInstance.load(0); fixture.detectChanges();
    expect(fixture.nativeElement.querySelector('.people')).toBeNull();
    http.expectOne('/api/hr/employees/overview?page=0&size=20').flush({}, { status: 403, statusText: 'Forbidden' });
    fixture.detectChanges(); expect(fixture.nativeElement.textContent).toContain('Seu acesso de RH não está disponível');
    expect(fixture.componentInstance.page()).toBeNull();
  });
});
