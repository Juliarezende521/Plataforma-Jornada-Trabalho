import { Component, computed, effect, ElementRef, inject, input, output, untracked, viewChild } from '@angular/core';
import { Identity } from '../auth/auth.service';
import { JourneyState } from './journey-state';
import { ACTION_LABELS, EventType } from './journey.types';

@Component({
  selector: 'app-journey', providers: [JourneyState],
  templateUrl: './journey.component.html', styleUrl: './journey.component.scss',
})
export class JourneyComponent {
  readonly user = input.required<Identity>();
  readonly busyChange = output<boolean>();
  readonly state = inject(JourneyState);
  readonly labels = ACTION_LABELS;
  private readonly heading = viewChild<ElementRef<HTMLElement>>('journeyHeading');
  readonly referenceZone = computed(() => this.state.current()?.session?.referenceTimeZoneId ?? this.user().referenceTimeZoneId);
  readonly statusLabel = computed(() => {
    const current = this.state.current();
    if (!current) return 'Jornada ainda não consultada';
    return current.session === null ? 'Sem jornada aberta' : current.session.state === 'PAUSED' ? 'Em pausa' : 'Trabalhando';
  });
  readonly lastType = computed(() => {
    const event = this.state.current()?.session?.lastEvent;
    return event ? this.labels[event.type] : '';
  });

  constructor() {
    effect(() => { const user = this.user(); untracked(() => this.state.connect(user)); });
    effect(() => this.busyChange.emit(this.state.busy()));
    effect(() => { if (this.state.focusCompletion() > 0) this.heading()?.nativeElement.focus(); });
  }

  formatInstant(instant: string): string {
    try {
      return new Intl.DateTimeFormat('pt-BR', { timeZone: this.referenceZone(),
        day: '2-digit', month: '2-digit', year: 'numeric', hour: '2-digit', minute: '2-digit', second: '2-digit',
        timeZoneName: 'shortOffset' }).format(new Date(instant));
    } catch { return 'Horário indisponível'; }
  }

  formatWorkDate(date: string): string { return date.split('-').reverse().join('/'); }

  formatDuration(duration: string): string {
    const match = /^PT(?:(\d+)H)?(?:(\d+)M)?(?:(\d+(?:\.\d+)?)S)?$/.exec(duration);
    if (!match) return 'Duração indisponível';
    const total = Number(match[1] ?? 0) * 3600 + Number(match[2] ?? 0) * 60 + Number(match[3] ?? 0);
    return `${Math.floor(total / 3600)} h ${Math.floor(total % 3600 / 60).toString().padStart(2, '0')} min`;
  }

  mark(type: EventType): void { void this.state.mark(type); }
}
