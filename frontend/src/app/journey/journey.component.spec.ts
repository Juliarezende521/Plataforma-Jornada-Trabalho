import { TestBed, ComponentFixture } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { JourneyComponent } from './journey.component';
import { AttemptDiary } from './attempt-diary';
import { Attempt, CurrentJourney, Receipt } from './journey.types';
import { Identity } from '../auth/auth.service';

const user: Identity = { userId: 'aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa', employeeId: 'bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb',
  name: 'Exemplo', email: 'exemplo@example.invalid', referenceTimeZoneId: 'Europe/Amsterdam' };
const NOW = '2026-10-14T14:00:00Z';
const sessionId = 'dddddddd-dddd-4ddd-dddd-dddddddddddd';
const empty: CurrentJourney = { session: null, observedAtUtc: NOW, allowedActions: ['ENTRY'] };
const working: CurrentJourney = { observedAtUtc: NOW, allowedActions: ['PAUSE_START', 'EXIT'], session: {
  id: sessionId, state: 'WORKING', workDate: '2026-10-14', referenceTimeZoneId: 'Europe/Amsterdam', openedAtUtc: NOW,
  lastEvent: { id: sessionId, type: 'ENTRY', occurredAtUtc: NOW, recordedTimeZoneId: 'Europe/Amsterdam' }, provisionalWorkDuration: 'PT1H30M' } };

function receipt(attempt: Attempt): Receipt {
  return { requestKey: attempt.requestKey, type: attempt.type, httpStatus: 201, code: 'RECORDED',
    workSessionId: sessionId, eventId: sessionId, occurredAtUtc: NOW, confirmedAtUtc: NOW };
}

describe('Journey screen and recovery protocol', () => {
  let fixture: ComponentFixture<JourneyComponent>;
  let http: HttpTestingController;
  let diary: AttemptDiary;
  let view: HTMLElement;
  const turn = async () => { await new Promise(resolve => setTimeout(resolve, 0)); fixture.detectChanges(); };

  beforeEach(async () => {
    localStorage.clear();
    await TestBed.configureTestingModule({ imports: [JourneyComponent], providers: [provideHttpClient(), provideHttpClientTesting()] }).compileComponents();
    http = TestBed.inject(HttpTestingController);
    diary = TestBed.inject(AttemptDiary);
  });
  afterEach(() => { fixture?.destroy(); http.verify(); vi.restoreAllMocks(); localStorage.clear(); });

  function mount() {
    fixture = TestBed.createComponent(JourneyComponent);
    fixture.componentRef.setInput('user', user);
    view = fixture.nativeElement as HTMLElement;
    fixture.detectChanges();
  }
  async function initial(current = empty) {
    mount();
    http.expectOne('/api/me').flush(user);
    await turn();
    http.expectOne('/api/me/work-sessions/current').flush(current);
    await turn();
  }
  async function prepareEntry() {
    const action = fixture.componentInstance.state.mark('ENTRY');
    http.expectOne('/api/me').flush(user);
    await turn();
    http.expectOne('/api/me/work-sessions/current').flush(empty);
    await turn();
    const attempt = diary.list(user)[0];
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'csrf-only-in-memory' });
    await turn();
    return { action, attempt, post: http.expectOne('/api/me/work-events') };
  }

  it('shows confirmed state and provisional duration with explicit reference zone', async () => {
    await initial(working);
    expect(view.textContent).toContain('Trabalhando');
    expect(view.textContent).toContain('Data de referência');
    expect(view.textContent).toContain('Europe/Amsterdam');
    expect(view.textContent).toContain('Última marcação confirmada');
    expect([...view.querySelectorAll('button')].map(button => button.textContent?.trim())).toEqual(['Iniciar pausa', 'Encerrar jornada', 'Atualizar jornada']);
  });

  it('shows a different recording zone on the latest marking', async () => {
    await initial({ ...working, session: { ...working.session!,
      lastEvent: { ...working.session!.lastEvent, recordedTimeZoneId: 'Europe/Lisbon' } } });
    expect(view.textContent).toContain('Fuso no registro: Europe/Lisbon');
    expect(view.textContent).toContain('Europe/Amsterdam');
  });

  it('shows the closed journey of today after loading and permits a new entry', async () => {
    await initial({ ...working, allowedActions: ['ENTRY'], session: { ...working.session!, state: 'CLOSED', lastEvent: { ...working.session!.lastEvent, type: 'EXIT' } } });
    expect(view.textContent).toContain('Encerrada');expect(view.textContent).toContain('Última marcação confirmada');
    expect([...view.querySelectorAll('button')].map(button => button.textContent?.trim())).toEqual(['Registrar entrada', 'Atualizar jornada']);
  });

  it('does not poll again when the same identity is refreshed as a new object', async () => {
    await initial();
    fixture.componentRef.setInput('user', { ...user });
    await turn();
    http.expectNone('/api/me'); http.expectNone('/api/me/work-sessions/current');
  });

  it('exige escolha explícita na interface antes de iniciar uma marcação', async () => {
    await initial();
    fixture.componentInstance.mark('ENTRY'); await turn();
    expect(view.textContent).toContain('Escolha a modalidade de trabalho');
    http.expectNone('/api/me/work-events'); http.expectNone('/api/me');
  });

  it('envia e preserva a modalidade da tentativa durante reenvio mesmo após outra escolha', async () => {
    const attempt = diary.save(user, { type: 'ENTRY', expectedWorkSessionId: null, timeZoneId: 'Europe/Lisbon', workMode: 'TRAVEL' });
    mount(); http.expectOne('/api/me').flush(user); await turn();
    http.expectOne('/api/me/work-event-requests/' + attempt.requestKey).flush({}, { status: 404, statusText: 'Not Found' }); await turn();
    http.expectOne('/api/me/work-sessions/current').flush(empty); await turn();
    fixture.componentInstance.chooseMode('OFFICE');
    const action = fixture.componentInstance.state.retry(attempt);
    http.expectOne('/api/me').flush(user); await turn();
    http.expectOne('/api/me/work-event-requests/' + attempt.requestKey).flush({}, { status: 404, statusText: 'Not Found' }); await turn();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'csrf' }); await turn();
    const post = http.expectOne('/api/me/work-events');
    expect(post.request.body.workMode).toBe('TRAVEL');
    expect(post.request.headers.get('Idempotency-Key')).toBe(attempt.requestKey);
    post.flush(receipt(attempt)); await turn();
    http.expectOne('/api/me/work-sessions/current').flush(working);
    await action; await turn();
  });

  it('preserves an attempt before POST, prevents double-click and waits for server confirmation', async () => {
    await initial();
    const { action, attempt, post } = await prepareEntry();
    expect(attempt.timeZoneId).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
    expect(post.request.headers.get('Idempotency-Key')).toBe(attempt.requestKey);
    expect(post.request.headers.get('X-CSRF-TOKEN')).toBe('csrf-only-in-memory');
    expect(post.request.body).toEqual({ type: 'ENTRY', expectedWorkSessionId: null, timeZoneId: attempt.timeZoneId });
    expect(JSON.stringify(diary.list(user))).not.toContain('csrf');
    void fixture.componentInstance.state.mark('ENTRY');
    http.expectNone('/api/me');
    expect(view.querySelector('[data-testid=journey-feedback]')?.textContent).not.toContain('confirmado pelo servidor');
    post.flush(receipt(attempt));
    await turn();
    http.expectOne('/api/me/work-sessions/current').flush(working);
    await action; await turn();
    expect(view.textContent).toContain('Registrar entrada: confirmado pelo servidor');
    expect(view.textContent).toContain('Registrar entrada: confirmado.');
    expect(diary.list(user)).toHaveLength(0);
  });

  it('preserves network failure and recovers receipt after remount without any POST', async () => {
    await initial();
    const { action, attempt, post } = await prepareEntry();
    post.error(new ProgressEvent('error'));
    await action; await turn();
    expect(view.textContent).toContain('tentativa foi preservada');
    fixture.destroy();
    mount();
    http.expectOne('/api/me').flush(user); await turn();
    http.expectOne('/api/me/work-event-requests/' + attempt.requestKey).flush(receipt(attempt)); await turn();
    http.expectOne('/api/me/work-sessions/current').flush(working); await turn();
    http.expectNone('/api/me/work-events');
    expect(diary.list(user)).toHaveLength(0);
    expect(view.textContent).toContain('Trabalhando');
  });

  it.each([undefined, 'Europe/Lisbon'])('keeps a missing receipt uncertain and retries with the original key and zone %s', async timeZoneId => {
    const attempt = diary.save(user, { type: 'ENTRY', expectedWorkSessionId: null, timeZoneId });
    vi.spyOn(Intl.DateTimeFormat.prototype, 'resolvedOptions').mockReturnValue({ timeZone: 'Asia/Tokyo' } as Intl.ResolvedDateTimeFormatOptions);
    mount();
    http.expectOne('/api/me').flush(user); await turn();
    http.expectOne('/api/me/work-event-requests/' + attempt.requestKey).flush({}, { status: 404, statusText: 'Not Found' }); await turn();
    http.expectOne('/api/me/work-sessions/current').flush(empty); await turn();
    expect(view.textContent).toContain('ausência de recibo não comprova');
    http.expectNone('/api/me/work-events');
    const action = fixture.componentInstance.state.retry(attempt);
    http.expectOne('/api/me').flush(user); await turn();
    http.expectOne('/api/me/work-event-requests/' + attempt.requestKey).flush({}, { status: 404, statusText: 'Not Found' }); await turn();
    http.expectOne('/api/auth/csrf').flush({ headerName: 'X-CSRF-TOKEN', token: 'new-csrf' }); await turn();
    const post = http.expectOne('/api/me/work-events');
    expect(post.request.headers.get('Idempotency-Key')).toBe(attempt.requestKey);
    expect(post.request.body).toEqual(timeZoneId === undefined ?
      { type: 'ENTRY', expectedWorkSessionId: null } : { type: 'ENTRY', expectedWorkSessionId: null, timeZoneId });
    post.flush(receipt(attempt)); await turn();
    http.expectOne('/api/me/work-sessions/current').flush(working);
    await action; await turn();
    expect(diary.list(user)).toHaveLength(0);
  });

  it('blocks sending if browser storage fails', async () => {
    await initial();
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new Error('storage unavailable'); });
    const action = fixture.componentInstance.state.mark('ENTRY');
    http.expectOne('/api/me').flush(user); await turn();
    http.expectOne('/api/me/work-sessions/current').flush(empty);
    await action; await turn();
    http.expectNone('/api/auth/csrf'); http.expectNone('/api/me/work-events');
    expect(view.textContent).toContain('permita o armazenamento');
    expect(fixture.componentInstance.state.ready()).toBe(false);
  });

  it('does not read or replay another identity diary', async () => {
    const other = { ...user, userId: 'cccccccc-cccc-4ccc-cccc-cccccccccccc' };
    const attempt = diary.save(other, { type: 'ENTRY', expectedWorkSessionId: null });
    await initial();
    http.expectNone('/api/me/work-event-requests/' + attempt.requestKey);
    expect(diary.list(other)).toHaveLength(1);
  });

  it('observes another tab and reconciles before enabling new actions', async () => {
    await initial();
    const attempt = diary.save(user, { type: 'ENTRY', expectedWorkSessionId: null });
    window.dispatchEvent(new StorageEvent('storage', { key: 'work-journey.online.v1.' + attempt.requestKey }));
    http.expectOne('/api/me').flush(user); await turn();
    http.expectOne('/api/me/work-event-requests/' + attempt.requestKey).flush({}, { status: 404, statusText: 'Not Found' }); await turn();
    http.expectOne('/api/me/work-sessions/current').flush(empty); await turn();
    expect(view.textContent).toContain('Marcação aguardando confirmação');
    expect(view.textContent).not.toContain('Pronto para começar?\nRegistrar entrada');
    http.expectNone('/api/me/work-events');
  });

  it('revalidates current session and never sends an old action to a new journey', async () => {
    await initial(working);
    const action = fixture.componentInstance.state.mark('EXIT');
    http.expectOne('/api/me').flush(user); await turn();
    http.expectOne('/api/me/work-sessions/current').flush({ ...working, session: { ...working.session!, id: 'eeeeeeee-eeee-4eee-eeee-eeeeeeeeeeee' } });
    await action; await turn();
    http.expectNone('/api/me/work-events');
    expect(diary.list(user)).toHaveLength(0);
    expect(view.textContent).toContain('A jornada mudou');
  });

  it('settles a definitive business rejection without pretending it was recorded', async () => {
    await initial();
    const { action, attempt, post } = await prepareEntry();
    post.flush({ ...receipt(attempt), httpStatus: 409, code: 'ALREADY_OPEN', workSessionId: null, eventId: null, occurredAtUtc: null }, { status: 409, statusText: 'Conflict' });
    await turn();
    http.expectOne('/api/me/work-sessions/current').flush(working);
    await action; await turn();
    expect(diary.list(user)).toHaveLength(0);
    expect(view.textContent).toContain('Já existe uma jornada aberta');
    expect(view.textContent).toContain('Registrar entrada: recusado.');
  });

  it('keeps an uncertain attempt on CSRF failure and never automatically replays it', async () => {
    await initial();
    const { action, attempt, post } = await prepareEntry();
    post.flush({}, { status: 403, statusText: 'Forbidden' });
    await action; await turn();
    expect(diary.list(user)).toEqual([attempt]);
    expect(view.textContent).toContain('Seu acesso precisa ser atualizado');
    http.expectNone('/api/auth/csrf'); http.expectNone('/api/me/work-events');
  });

  it('does not discard a KEY_REUSED result as a terminal receipt', async () => {
    await initial();
    const { action, attempt, post } = await prepareEntry();
    post.flush({ ...receipt(attempt), httpStatus: 409, code: 'KEY_REUSED', workSessionId: null, eventId: null, occurredAtUtc: null }, { status: 409, statusText: 'Conflict' });
    await turn();
    http.expectOne('/api/me/work-sessions/current').flush(empty);
    await action; await turn();
    expect(diary.list(user)).toHaveLength(1);
    expect(view.textContent).toContain('não corresponde ao resultado original');
  });
});
