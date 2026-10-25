import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TeamAssignmentComponent } from './team-assignment.component';

describe('TeamAssignmentComponent', () => {
  let fixture: ComponentFixture<TeamAssignmentComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [TeamAssignmentComponent], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(TeamAssignmentComponent);
    fixture.componentRef.setInput('employee', { id: 'worker', name: 'Colaboradora', referenceTimeZoneId: 'UTC' });
    fixture.detectChanges();
    http.expectOne('/api/hr/employees?page=0&size=100').flush({
      items: [
        { id: 'worker', name: 'Colaboradora', referenceTimeZoneId: 'UTC' },
        { id: 'manager', name: 'Gestora', referenceTimeZoneId: 'UTC' },
      ], page: 0, size: 100, totalElements: 2,
    });
    http.expectOne('/api/hr/employees/worker/manager-assignment').flush(null);
    fixture.detectChanges();
  });

  afterEach(() => http.verify());

  it('excludes self-management and creates an audited version through the protected API', () => {
    expect(fixture.componentInstance.candidates().map(person => person.id)).toEqual(['manager']);
    fixture.componentInstance.form.setValue({ managerEmployeeId: 'manager', effectiveFrom: '2026-11-01' });
    fixture.componentInstance.save();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'token' });
    const request = http.expectOne('/api/hr/employees/worker/manager-assignments');
    expect(request.request.method).toBe('POST');
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('token');
    expect(request.request.body).toEqual({ managerEmployeeId: 'manager', effectiveFrom: '2026-11-01' });
    request.flush({ id: 'assignment', employeeId: 'worker', managerEmployeeId: 'manager', managerName: 'Gestora', effectiveFrom: '2026-11-01', createdAtUtc: '2026-10-25T12:00:00Z' });
    expect(fixture.componentInstance.message()).toContain('registrada e auditada');
  });
});
