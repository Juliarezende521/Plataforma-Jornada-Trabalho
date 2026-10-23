import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { AuditLogComponent } from './audit-log.component';

describe('Audit log', () => {
  let fixture: ComponentFixture<AuditLogComponent>;
  let http: HttpTestingController;
  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [AuditLogComponent], providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(AuditLogComponent);
    fixture.detectChanges();
  });
  afterEach(() => { fixture.destroy(); http.verify(); });

  it('loads a read-only paginated trail and presents safe labels', () => {
    const request = http.expectOne('/api/hr/audit-entries?page=0&size=20');
    expect(request.request.method).toBe('GET');
    request.flush({ items: [{ id: 'audit-1', actorUserId: 'actor-1', actorEmail: 'rh@example.invalid',
      action: 'WORK_SCHEDULE_EXCEPTION_CREATED', entityType: 'WORK_SCHEDULE_EXCEPTION', entityId: 'exception-1',
      recordedAtUtc: '2026-10-23T12:00:00Z', details: 'workDate=2026-12-25' }], page: 0, size: 20, totalElements: 21 });
    fixture.detectChanges();
    expect(fixture.nativeElement.textContent).toContain('Exceção de escala cadastrada');
    expect(fixture.nativeElement.textContent).toContain('rh@example.invalid');
    expect(fixture.nativeElement.textContent).toContain('Página 1 · 21 registros');
    fixture.componentInstance.load(1);
    http.expectOne('/api/hr/audit-entries?page=1&size=20').flush({ items: [], page: 1, size: 20, totalElements: 21 });
  });

  it('removes stale audit data when access is denied', () => {
    http.expectOne('/api/hr/audit-entries?page=0&size=20').flush({ items: [], page: 0, size: 20, totalElements: 0 });
    fixture.componentInstance.load(0);
    http.expectOne('/api/hr/audit-entries?page=0&size=20').flush({}, { status: 403, statusText: 'Forbidden' });
    expect(fixture.componentInstance.page()).toBeNull();
    expect(fixture.componentInstance.message()).toContain('não permite');
  });
});
