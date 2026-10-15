import { Identity } from '../auth/auth.service';

export const EVENT_TYPES = ['ENTRY', 'PAUSE_START', 'PAUSE_END', 'EXIT'] as const;
export type EventType = typeof EVENT_TYPES[number];
export interface MarkingCommand { type: EventType; expectedWorkSessionId: string | null; timeZoneId?: string; }
export interface Attempt extends MarkingCommand { version: 1; requestKey: string; userId: string; employeeId: string; }
export interface Receipt {
  requestKey: string; type: EventType; httpStatus: number; code: string;
  workSessionId: string | null; eventId: string | null; occurredAtUtc: string | null; confirmedAtUtc: string;
}
export interface CurrentJourney {
  session: {
    id: string; state: 'WORKING' | 'PAUSED' | 'CLOSED'; workDate: string; referenceTimeZoneId: string;
    openedAtUtc: string; lastEvent: { id: string; type: EventType; occurredAtUtc: string; recordedTimeZoneId: string };
    provisionalWorkDuration: string;
  } | null;
  observedAtUtc: string;
  allowedActions: EventType[];
}
export const ACTION_LABELS: Record<EventType, string> = {
  ENTRY: 'Registrar entrada', PAUSE_START: 'Iniciar pausa', PAUSE_END: 'Retomar trabalho', EXIT: 'Encerrar jornada',
};
export function sameIdentity(first: Identity | null, second: Identity): boolean {
  return first?.userId === second.userId && first.employeeId === second.employeeId;
}
