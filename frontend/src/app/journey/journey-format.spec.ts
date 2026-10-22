import { formatInstant, formatDuration, formatSignedSeconds } from './journey-format';
describe('Temporal presentation', () => {
  it('distinguishes repeated Amsterdam hours by offset and preserves total hours beyond one day', () => {
    const first=formatInstant('2026-10-25T00:30:00Z','Europe/Amsterdam');
    const second=formatInstant('2026-10-25T01:30:00Z','Europe/Amsterdam');
    expect(first).toContain('02:30:00'); expect(first).toContain('GMT+2');
    expect(second).toContain('02:30:00'); expect(second).toContain('GMT+1');
    expect(formatDuration('PT26H30M0.0000001S')).toBe('26 h 30 min');
  });
  it('formats positive, negative and zero period differences without hiding the sign', () => {
    expect(formatSignedSeconds(5400)).toBe('+1 h 30 min');
    expect(formatSignedSeconds(-1800)).toBe('−0 h 30 min');
    expect(formatSignedSeconds(0)).toBe('0 h 00 min');
  });
});
