import { TestBed } from '@angular/core/testing';
import { JourneyClockComponent } from './journey-clock.component';

describe('Journey device clock', () => {
  afterEach(() => { vi.useRealTimers(); vi.restoreAllMocks(); });

  it('updates from the device time each second, exposes the zone and releases its timer', () => {
    vi.useFakeTimers();
    vi.setSystemTime(new Date('2026-10-15T02:59:59Z'));
    const fixture = TestBed.createComponent(JourneyClockComponent);
    expect(fixture.componentInstance.zone()).toBe(Intl.DateTimeFormat().resolvedOptions().timeZone);
    fixture.componentInstance.zone.set('America/Sao_Paulo');
    fixture.detectChanges();
    const view = fixture.nativeElement as HTMLElement;
    expect(view.querySelector('time')?.textContent).toBe('23:59:59');
    expect(view.textContent).toContain('America/Sao_Paulo');
    expect(view.querySelector('[aria-live]')?.getAttribute('aria-live')).toBe('off');
    const timers = vi.getTimerCount();
    vi.advanceTimersByTime(1000);
    fixture.componentInstance.zone.set('America/Sao_Paulo');
    fixture.detectChanges();
    expect(view.querySelector('time')?.textContent).toBe('00:00:00');
    expect(view.querySelector('time')?.getAttribute('datetime')).toBe('2026-10-15T03:00:00.000Z');
    fixture.destroy();
    expect(vi.getTimerCount()).toBeLessThan(timers);
  });
});
