import { AttemptDiary } from './attempt-diary';
import { Identity } from '../auth/auth.service';

const identity: Identity = { userId: 'aaaaaaaa-aaaa-4aaa-aaaa-aaaaaaaaaaaa', employeeId: 'bbbbbbbb-bbbb-4bbb-bbbb-bbbbbbbbbbbb',
  email: 'example@example.invalid', name: 'Exemplo', referenceTimeZoneId: 'Europe/Amsterdam' };

describe('Minimal attempt diary', () => {
  const diary = new AttemptDiary();
  beforeEach(() => localStorage.clear());
  afterEach(() => { vi.restoreAllMocks(); localStorage.clear(); });

  it('preserves only recovery fields and scopes every attempt to both identity IDs', () => {
    const first = diary.save(identity, { type: 'ENTRY', expectedWorkSessionId: null });
    const other = { ...identity, employeeId: 'cccccccc-cccc-4ccc-cccc-cccccccccccc' };
    diary.save(other, { type: 'ENTRY', expectedWorkSessionId: null });
    expect(diary.list(identity)).toEqual([first]);
    expect(Object.keys(first).sort()).toEqual(['employeeId', 'expectedWorkSessionId', 'requestKey', 'type', 'userId', 'version']);
    expect(JSON.stringify(first)).not.toContain(identity.email);
    expect(JSON.stringify(first)).not.toContain(identity.name);
    diary.remove(first);
    expect(diary.list(identity)).toEqual([]);
    expect(diary.list(other)).toHaveLength(1);
  });

  it('does not overwrite another attempt and keeps keys distinct', () => {
    const first = diary.save(identity, { type: 'ENTRY', expectedWorkSessionId: null });
    const second = diary.save(identity, { type: 'ENTRY', expectedWorkSessionId: null });
    expect(first.requestKey).not.toBe(second.requestKey);
    expect(diary.list(identity)).toHaveLength(2);
  });

  it('rejects changed payloads instead of silently removing them', () => {
    const attempt = diary.save(identity, { type: 'ENTRY', expectedWorkSessionId: null });
    const key = localStorage.key(0)!;
    localStorage.setItem(key, JSON.stringify({ ...attempt, userId: 'cccccccc-cccc-4ccc-cccc-cccccccccccc' }));
    expect(() => diary.remove(attempt)).toThrow();
    expect(localStorage.getItem(key)).not.toBeNull();
  });

  it.each(['null', '{}', 'invalid JSON'])('blocks malformed storage: %s', value => {
    localStorage.setItem('work-journey.online.v1.' + crypto.randomUUID(), value);
    expect(() => diary.list(identity)).toThrow();
  });

  it('rejects extra sensitive fields and mismatched storage keys', () => {
    const attempt = diary.save(identity, { type: 'ENTRY', expectedWorkSessionId: null });
    const key = localStorage.key(0)!;
    localStorage.setItem(key, JSON.stringify({ ...attempt, password: 'forbidden' }));
    expect(() => diary.list(identity)).toThrow();
    localStorage.setItem(key, JSON.stringify({ ...attempt, requestKey: crypto.randomUUID() }));
    expect(() => diary.list(identity)).toThrow();
  });

  it('requires successful persistence and read-back', () => {
    vi.spyOn(Storage.prototype, 'setItem').mockImplementation(() => { throw new DOMException('Quota exceeded'); });
    expect(() => diary.save(identity, { type: 'ENTRY', expectedWorkSessionId: null })).toThrow();
  });
});
