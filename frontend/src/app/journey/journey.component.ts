import { Component, computed, effect, ElementRef, inject, input, output, untracked, viewChild } from '@angular/core';
import { formatInstant, formatWorkDate, formatDuration } from './journey-format';
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
  readonly referenceZone = computed(() => this.user().referenceTimeZoneId);
  readonly statusLabel = computed(() => {
    const current = this.state.current();
    if (!current) return 'Jornada ainda não consultada';
    return current.session === null ? 'Sem jornada aberta' : current.session.state === 'CLOSED' ? 'Encerrada' : current.session.state === 'PAUSED' ? 'Em pausa' : 'Trabalhando';
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

  formatInstant(instant: string): string { return formatInstant(instant, this.referenceZone()); }
  readonly formatWorkDate = formatWorkDate;
  readonly formatDuration = formatDuration;

  focusHeading(): void { this.heading()?.nativeElement.focus(); }

  mark(type: EventType): void { void this.state.mark(type); }
}
