import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { EmploymentContractComponent } from './employment-contract.component';
describe('Employment contract', () => {
  let fixture: ComponentFixture<EmploymentContractComponent>;
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [EmploymentContractComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(EmploymentContractComponent);
    fixture.componentRef.setInput('employeeId', 'employee-1');
    fixture.detectChanges();
  });
  afterEach(() => {
    fixture.destroy();
    http.verify();
  });
  it('creates an audited version with CSRF', () => {
    fixture.componentInstance.form.setValue({
      effectiveFrom: '2026-11-01',
      countryCode: 'BR',
      employmentType: 'FULL_TIME',
      weeklyMinutes: 2400,
    });
    fixture.componentInstance.save();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
    const request = http.expectOne('/api/hr/employees/employee-1/employment-contracts');
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('csrf');
    expect(request.request.body.weeklyMinutes).toBe(2400);
    request.flush({
      id: 'c1',
      employeeId: 'employee-1',
      effectiveFrom: '2026-11-01',
      countryCode: 'BR',
      employmentType: 'FULL_TIME',
      weeklyMinutes: 2400,
      createdAtUtc: '2026-10-23T12:00:00Z',
    });
    expect(fixture.componentInstance.message()).toContain('sucesso');
  });
  it('loads current and paginated history only when requested', () => {
    http.expectNone('/api/hr/employees/employee-1/employment-contract');
    fixture.componentInstance.loadCurrent();
    http.expectOne('/api/hr/employees/employee-1/employment-contract').flush(null);
    fixture.componentInstance.loadHistory();
    const request = http.expectOne((r) => r.url.endsWith('/employment-contracts'));
    expect(request.request.params.get('size')).toBe('20');
    request.flush({ items: [], page: 0, size: 20, totalElements: 0 });
  });
});
