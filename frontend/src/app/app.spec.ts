import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { provideHttpClientTesting, HttpTestingController } from '@angular/common/http/testing';
import { App } from './app';

describe('Access screen', () => {
  let fixture: ComponentFixture<App>;
  let http: HttpTestingController;
  let view: HTMLElement;
  beforeEach(async () => {
    await TestBed.configureTestingModule({ imports: [App], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(App);
    view = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  });
  afterEach(() => http.verify());

  function anonymous() {
    http.expectOne('/api/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
  }
  function fill(email = 'brasil@example.invalid', password = 'test-password') {
    for (const [id, value] of [['email', email], ['password', password]]) {
      const input = view.querySelector<HTMLInputElement>(`#${id}`)!;
      input.value = value;
      input.dispatchEvent(new Event('input'));
    }
    fixture.detectChanges();
  }
  function submit() {
    view.querySelector('#login-form')!.dispatchEvent(new Event('submit', { bubbles: true, cancelable: true }));
    fixture.detectChanges();
  }

  it('checks the session before showing the form', () => {
    expect(view.textContent).toContain('Verificando seu acesso');
    expect(view.querySelector('#login-form')).toBeNull();
    anonymous();
    expect(view.querySelector('#login-form')).not.toBeNull();
    expect(view.querySelector('input[type=password]')?.getAttribute('autocomplete')).toBe('current-password');
  });

  it('validates required inputs without calling login', () => {
    anonymous(); submit();
    expect(view.textContent).toContain('Informe um email válido');
    expect(view.textContent).toContain('Informe sua senha');
    http.expectNone('/api/auth/csrf');
  });

  it('blocks a password over the backend byte limit', () => {
    anonymous(); fill('brasil@example.invalid', 'é'.repeat(37)); submit();
    expect(view.textContent).toContain('muito longa');
    http.expectNone('/api/auth/csrf');
  });

  it('locks the form while pending, shows a generic error and clears the password', () => {
    anonymous(); fill(); submit(); submit();
    expect(view.querySelector('fieldset')?.disabled).toBe(true);
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'token' });
    http.expectOne('/api/auth/login').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(view.textContent).toContain('Confira seu email e senha');
    expect(view.querySelector<HTMLInputElement>('#password')?.value).toBe('');
    expect(view.querySelector('fieldset')?.disabled).toBe(false);
  });

  it('shows the authenticated identity and logs out', async () => {
    anonymous(); fill(); submit();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'before' });
    http.expectOne('/api/auth/login').flush(null, { status: 204, statusText: 'No Content' });
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'after' });
    http.expectOne('/api/me').flush({ userId: 'u', employeeId: 'e', name: 'Colaborador Brasil', email: 'brasil@example.invalid', referenceTimeZoneId: 'America/Sao_Paulo' });
    fixture.detectChanges();
    http.expectOne('/api/me/work-schedule').flush(null);
    expect(view.textContent).toContain('Olá, Colaborador Brasil');
    expect(view.textContent).toContain('Jornada atual');
    expect(view.querySelector('#login-form')).toBeNull();
    http.expectOne('/api/me').flush({ userId: 'u', employeeId: 'e', name: 'Colaborador Brasil', email: 'brasil@example.invalid', referenceTimeZoneId: 'America/Sao_Paulo' });
    await new Promise(resolve => setTimeout(resolve, 0));
    http.expectOne('/api/me/work-sessions/current').flush({ session: null, observedAtUtc: '2026-10-14T14:00:00Z', allowedActions: ['ENTRY'] });
    await new Promise(resolve => setTimeout(resolve, 0));
    fixture.detectChanges();
    view.querySelector('button')!.click();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'logout' });
    http.expectOne('/api/auth/logout').flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();
    expect(view.textContent).toContain('Você saiu');
    expect(view.querySelector('#login-form')).not.toBeNull();
  });

  it('opens HR directly without mounting personal journey views', () => {
    http.expectOne('/api/me').flush({ userId: 'h', employeeId: 'he', name: 'Recursos Humanos', email: 'rh@example.invalid', referenceTimeZoneId: 'America/Sao_Paulo', hrReader: true });
    fixture.detectChanges();
    http.expectOne('/api/hr/employees/overview?page=0&size=20').flush({ items: [], page: 0, size: 20, totalElements: 0, totalPages: 0 });
    fixture.detectChanges();
    expect(view.querySelector('app-hr-dashboard')).not.toBeNull();
    expect(view.querySelector('app-journey')).toBeNull();
    expect(view.querySelector('app-history')).toBeNull();
    expect(view.querySelector('nav[aria-label="Navegação das jornadas"]')).toBeNull();
    http.expectNone('/api/me/work-sessions/current');
  });

  it('offers session recovery after an unavailable backend', () => {
    http.expectOne('/api/me').flush({}, { status: 503, statusText: 'Unavailable' });
    fixture.detectChanges();
    expect(view.textContent).toContain('Acesso indisponível');
    view.querySelector('button')!.click();
    http.expectOne('/api/me').flush({}, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();
    expect(view.querySelector('#login-form')).not.toBeNull();
  });

  it('provides guidance for rate limited login', () => {
    anonymous(); fill(); submit();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'token' });
    http.expectOne('/api/auth/login').flush({}, { status: 429, statusText: 'Too Many Requests' });
    fixture.detectChanges();
    expect(view.textContent).toContain('Aguarde um minuto');
  });
});
