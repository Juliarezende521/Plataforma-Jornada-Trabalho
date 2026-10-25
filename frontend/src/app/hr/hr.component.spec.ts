import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { HrComponent } from './hr.component';
const hr = { userId: 'hr', employeeId: 'hr-employee', name: 'RH', email: 'hr@example.invalid', referenceTimeZoneId: 'UTC', hrReader: true };
const brazil = { id: 'brazil', name: 'Brasil', referenceTimeZoneId: 'America/Sao_Paulo' };
const europe = { id: 'europe', name: 'Europa', referenceTimeZoneId: 'Europe/Amsterdam' };
const emptySummary={from:'2026-10-01',to:'2026-10-14',totalWorkDuration:'PT0S',plannedWorkDuration:'PT0S',balanceSeconds:null,toleranceSeconds:null,withinTolerance:null,scheduledDays:0,periodDays:14,totalSessions:0,closedSessions:0,openSessions:0,adjustedSessions:0,workedDays:0,observedAtUtc:'2026-10-14T14:00:00Z'};
describe('Read-only HR screen', () => {
  let fixture: ComponentFixture<HrComponent>; let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [HrComponent], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController); fixture = TestBed.createComponent(HrComponent);
  });
  afterEach(() => { fixture.destroy(); http.verify(); });
  function mount(user = hr) { fixture.componentRef.setInput('user', user); fixture.detectChanges(); }
  function directory() {
    http.expectOne(r => r.url === '/api/hr/employees').flush({ items: [brazil, europe], page: 0, size: 20, totalElements: 2 }); fixture.detectChanges();
  }
  function teamAssignment(employeeId: string) {
    http.expectOne('/api/hr/employees?page=0&size=100').flush({ items: [brazil, europe], page: 0, size: 100, totalElements: 2 });
    http.expectOne(`/api/hr/employees/${employeeId}/manager-assignment`).flush(null);
    fixture.detectChanges();
  }
  it('loads a bounded directory and routes selected history through HR without changing authenticated identity', () => {
    mount(); directory(); fixture.componentInstance.choice.setValue(brazil.id); fixture.detectChanges();
    teamAssignment(brazil.id);
    http.expectOne('/api/hr/employees/brazil/work-schedule').flush(null);
    expect(fixture.nativeElement.textContent).toContain('Registros de Brasil');
    const from = fixture.nativeElement.querySelector('#history-from') as HTMLInputElement;
    const to = fixture.nativeElement.querySelector('#history-to') as HTMLInputElement;
    from.value = '2026-10-01'; from.dispatchEvent(new Event('input'));
    to.value = '2026-10-14'; to.dispatchEvent(new Event('input'));
    fixture.nativeElement.querySelector('app-history form').dispatchEvent(new Event('submit'));
    const request = http.expectOne(r => r.url === '/api/hr/employees/brazil/work-sessions');
    expect(request.request.params.get('from')).toBe('2026-10-01');
    request.flush({ items: [], page: 0, size: 20, totalElements: 0, observedAtUtc: '2026-10-14T14:00:00Z' });
    http.expectOne(r=>r.url==='/api/hr/employees/brazil/work-sessions/summary').flush(emptySummary);
    http.expectNone('/api/me/work-sessions');
    expect(fixture.componentInstance.user().userId).toBe('hr');
  });
  it('cancels pending history and clears selected data when switching employees or accounts', () => {
    mount(); directory(); fixture.componentInstance.choice.setValue(brazil.id); fixture.detectChanges();
    const scheduleRequest = http.expectOne('/api/hr/employees/brazil/work-schedule');
    const teamRequest = http.expectOne('/api/hr/employees?page=0&size=100');
    fixture.nativeElement.querySelector('app-history form').dispatchEvent(new Event('submit'));
    const request = http.expectOne(r => r.url === '/api/hr/employees/brazil/work-sessions');
    const summaryRequest=http.expectOne(r=>r.url==='/api/hr/employees/brazil/work-sessions/summary');
    fixture.componentInstance.choice.setValue(europe.id); fixture.detectChanges();
    expect(request.cancelled).toBe(true); expect(summaryRequest.cancelled).toBe(true); expect(scheduleRequest.cancelled).toBe(true); expect(teamRequest.cancelled).toBe(true);
    expect(fixture.nativeElement.textContent).toContain('Registros de Europa');
    const nextScheduleRequest = http.expectOne('/api/hr/employees/europe/work-schedule');
    const nextTeamRequest = http.expectOne('/api/hr/employees?page=0&size=100');
    fixture.componentRef.setInput('user', { ...hr, hrReader: false }); fixture.detectChanges();
    expect(nextScheduleRequest.cancelled).toBe(true); expect(nextTeamRequest.cancelled).toBe(true);
    expect(fixture.nativeElement.querySelector('app-history')).toBeNull(); expect(fixture.componentInstance.directory()).toBeNull();
    http.expectNone('/api/hr/employees');
  });
  it('does not query for a plain employee and clears the directory on forbidden access', () => {
    mount({ ...hr, hrReader: false }); http.expectNone('/api/hr/employees');
    fixture.componentRef.setInput('user', hr); fixture.detectChanges();
    http.expectOne(r => r.url === '/api/hr/employees').flush({}, { status: 403, statusText: 'Forbidden' }); fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Seu acesso de RH não está disponível');
    expect(fixture.componentInstance.directory()).toBeNull(); expect(fixture.componentInstance.selected()).toBeNull();
  });
});
