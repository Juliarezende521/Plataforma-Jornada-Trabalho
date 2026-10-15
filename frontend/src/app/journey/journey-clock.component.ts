import { Component, DestroyRef, computed, inject, signal } from '@angular/core';

@Component({
  selector: 'app-journey-clock',
  template: `
    <div class="clock" aria-live="off">
      <p class="label">Horário atual no dispositivo</p>
      <time [attr.datetime]="now().toISOString()">{{ displayTime() }}</time>
      <p class="zone">{{ zone() }} · As marcações usam o horário do servidor.</p>
    </div>
  `,
  styles: `
    :host { display: block; min-width: 0; }
    .clock { margin-block: 1.5rem; }
    .label, .zone { color: var(--color-muted); margin: 0; }
    .label { font-size: .75rem; text-transform: uppercase; letter-spacing: .06em; font-weight: 700; }
    time { display: block; font-family: ui-monospace, SFMono-Regular, Consolas, monospace;
      font-size: clamp(2.25rem, 9vw, 4rem); font-weight: 650; font-variant-numeric: tabular-nums;
      line-height: 1.4; letter-spacing: -.04em; }
    .zone { font-size: .8rem; overflow-wrap: anywhere; }
  `,
})
export class JourneyClockComponent {
  readonly now = signal(new Date());
  readonly zone = signal(Intl.DateTimeFormat().resolvedOptions().timeZone);
  private readonly formatter = computed(() => new Intl.DateTimeFormat('pt-BR', {
    timeZone: this.zone(), hour: '2-digit', minute: '2-digit', second: '2-digit', hourCycle: 'h23',
  }));
  readonly displayTime = computed(() => this.formatter().format(this.now()));

  constructor() {
    const interval = setInterval(() => {
      this.zone.set(Intl.DateTimeFormat().resolvedOptions().timeZone);
      this.now.set(new Date());
    }, 1000);
    inject(DestroyRef).onDestroy(() => clearInterval(interval));
  }
}
