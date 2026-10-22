import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { WorkScheduleComponent } from './work-schedule.component';
import { WorkSchedule } from './work-schedule.types';

const schedule: WorkSchedule = {
  id: 'schedule-1', employeeId: 'employee-1', effectiveFrom: '2026-10-20',
  minutes: [480, 480, 480, 480, 420, 0, 0], createdAtUtc: '2026-10-20T12:00:00Z',
};

describe('Work schedule', () => {
  let fixture: ComponentFixture<WorkScheduleComponent>;
  let http: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [WorkScheduleComponent],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
  });

  afterEach(() => {
    fixture?.destroy();
    http.verify();
  });

  function mount(employeeId: string | null = null, editable = false): void {
    fixture = TestBed.createComponent(WorkScheduleComponent);
    fixture.componentRef.setInput('employeeId', employeeId);
    fixture.componentRef.setInput('editable', editable);
    fixture.detectChanges();
  }

  it('shows the authenticated employee schedule without exposing the edit form', () => {
    mount();
    http.expectOne('/api/me/work-schedule').flush(schedule);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('20/10/2026');
    expect(fixture.nativeElement.textContent).toContain('7 h');
    expect(fixture.nativeElement.textContent).toContain('Folga');
    expect(fixture.nativeElement.querySelector('form')).toBeNull();
  });

  it('creates a versioned schedule with converted minutes and CSRF protection', () => {
    mount('employee/1', true);
    http.expectOne('/api/hr/employees/employee%2F1/work-schedule').flush(null);
    fixture.componentInstance.form.patchValue({ effectiveFrom: '2026-11-01', friday: '07:30' });
    fixture.componentInstance.save();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' });
    const request = http.expectOne('/api/hr/employees/employee%2F1/work-schedules');
    expect(request.request.headers.get('X-CSRF-TOKEN')).toBe('csrf-token');
    expect(request.request.body).toEqual({
      effectiveFrom: '2026-11-01', mondayMinutes: 480, tuesdayMinutes: 480,
      wednesdayMinutes: 480, thursdayMinutes: 480, fridayMinutes: 450,
      saturdayMinutes: 0, sundayMinutes: 0,
    });
    request.flush(schedule);
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Nova escala cadastrada com sucesso.');
  });

  it('rejects an all-zero schedule before sending any write request', () => {
    mount('employee-1', true);
    http.expectOne('/api/hr/employees/employee-1/work-schedule').flush(null);
    fixture.componentInstance.form.setValue({
      effectiveFrom: '2026-11-01', monday: '00:00', tuesday: '00:00', wednesday: '00:00',
      thursday: '00:00', friday: '00:00', saturday: '00:00', sunday: '00:00',
    });
    fixture.componentInstance.save();
    expect(fixture.componentInstance.message()).toContain('pelo menos um dia');
    http.expectNone('/api/auth/csrf');
  });

  it('cancels the previous read when the selected employee changes', () => {
    mount('employee-1', true);
    const first = http.expectOne('/api/hr/employees/employee-1/work-schedule');
    fixture.componentRef.setInput('employeeId', 'employee-2');
    fixture.detectChanges();
    expect(first.cancelled).toBe(true);
    http.expectOne('/api/hr/employees/employee-2/work-schedule').flush(schedule);
  });

  it('explains a duplicate effective date without replacing the current schedule', () => {
    mount('employee-1', true);
    http.expectOne('/api/hr/employees/employee-1/work-schedule').flush(schedule);
    fixture.componentInstance.form.patchValue({ effectiveFrom: '2026-10-20' });
    fixture.componentInstance.save();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'csrf-token' });
    http.expectOne('/api/hr/employees/employee-1/work-schedules').flush({}, { status: 409, statusText: 'Conflict' });
    expect(fixture.componentInstance.message()).toContain('Já existe uma escala');
    expect(fixture.componentInstance.schedule()?.id).toBe(schedule.id);
  });
});
