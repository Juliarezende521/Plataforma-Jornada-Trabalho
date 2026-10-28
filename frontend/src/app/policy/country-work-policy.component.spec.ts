import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { CountryWorkPolicyComponent } from './country-work-policy.component';

describe('Country work policy', () => {
  let fixture: ComponentFixture<CountryWorkPolicyComponent>; let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [CountryWorkPolicyComponent], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    fixture = TestBed.createComponent(CountryWorkPolicyComponent); fixture.detectChanges(); http = TestBed.inject(HttpTestingController);
    fixture.componentInstance.form.controls.effectiveFrom.setValue('2026-11-01');
  });
  afterEach(() => { fixture.destroy(); http.verify(); });
  it('creates a version with csrf and presents the current policy', () => {
    fixture.componentInstance.save();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'csrf' });
    const request = http.expectOne('/api/hr/work-policies');
    expect(request.request.method).toBe('POST'); expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('csrf');
    request.flush(policy());
    expect(fixture.componentInstance.current()?.countryCode).toBe('BR');
    expect(fixture.componentInstance.message()).toContain('cadastrada');
  });
  it('consults current and paginated history only on demand', () => {
    fixture.componentInstance.loadCurrent();
    http.expectOne(request => request.url === '/api/hr/work-policies/current'
      && request.params.get('countryCode') === 'BR' && /^\d{4}-\d{2}-\d{2}$/.test(request.params.get('on') ?? '')).flush(policy());
    fixture.componentInstance.loadHistory();
    http.expectOne('/api/hr/work-policies?countryCode=BR&page=0&size=20').flush({ items: [policy()], page: 0, size: 20, totalElements: 1 });
    expect(fixture.componentInstance.history()?.totalElements).toBe(1);
  });
  it('blocks invalid limits in the browser', () => {
    fixture.componentInstance.form.controls.maximumDailyMinutes.setValue(1441);
    fixture.componentInstance.save(); http.expectNone('/api/auth/csrf');
    expect(fixture.componentInstance.message()).toContain('Revise');
  });
});
function policy() { return { id: 'p1', countryCode: 'BR', effectiveFrom: '2026-11-01', standardWeeklyMinutes: 2400,
  maximumDailyMinutes: 600, minimumBreakMinutes: 60, overtimeToleranceMinutes: 10, createdAtUtc: '2026-10-28T12:00:00Z' }; }
