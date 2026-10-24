import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { MonthlyClosureComponent } from './monthly-closure.component';

describe('Monthly closure', () => {
  let fixture: ComponentFixture<MonthlyClosureComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [MonthlyClosureComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(MonthlyClosureComponent);
    fixture.componentRef.setInput('employeeId', 'employee-1');
    fixture.componentRef.setInput('timeZone', 'America/Sao_Paulo');
    fixture.detectChanges();
    fixture.componentInstance.competence.setValue('2026-09');
  });

  afterEach(() => {
    fixture.destroy();
    http.verify();
  });

  it('closes a competence with CSRF and presents the immutable snapshot', () => {
    fixture.componentInstance.close();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
    const request = http.expectOne('/api/hr/employees/employee-1/monthly-closures/2026-09');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('csrf');
    request.flush(closure());

    expect(fixture.componentInstance.selected()?.status).toBe('CLOSED');
    expect(fixture.componentInstance.message()).toContain('sucesso');
  });

  it('consults one competence and paginates history only on demand', () => {
    fixture.componentInstance.consult();
    http.expectOne('/api/hr/employees/employee-1/monthly-closures/2026-09').flush(null);
    expect(fixture.componentInstance.message()).toContain('aberta');

    fixture.componentInstance.loadHistory();
    const request = http.expectOne(r => r.url.endsWith('/monthly-closures'));
    expect(request.request.params.get('size')).toBe('20');
    request.flush({ items: [closure()], page: 0, size: 20, totalElements: 1 });
    expect(fixture.componentInstance.history()?.totalElements).toBe(1);
  });

  it('explains why an incomplete competence cannot be closed', () => {
    fixture.componentInstance.close();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
    http.expectOne('/api/hr/employees/employee-1/monthly-closures/2026-09').flush(
      { title: 'Monthly period has incomplete schedule coverage' },
      { status: 409, statusText: 'Conflict' },
    );

    expect(fixture.componentInstance.message()).toContain('escala não cobre');
  });
});

function closure() {
  return {
    id: 'closure-1', employeeId: 'employee-1', competence: '2026-09',
    periodFrom: '2026-09-01', periodTo: '2026-09-30', totalWorkSeconds: 576000,
    plannedWorkSeconds: 576000, balanceSeconds: 0, toleranceSeconds: 12000,
    withinTolerance: true, totalSessions: 20, adjustedSessions: 1, workedDays: 20,
    status: 'CLOSED', closedAtUtc: '2026-10-01T12:00:00Z',
  };
}
