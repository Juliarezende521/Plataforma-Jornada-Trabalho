import { Identity } from '../auth/auth.service';

export const EVENT_TYPES = ['ENTRY', 'PAUSE_START', 'PAUSE_END', 'EXIT'] as const;
export type EventType = typeof EVENT_TYPES[number];
export const WORK_MODES = ['OFFICE', 'REMOTE', 'TRAVEL'] as const;
export type WorkMode = typeof WORK_MODES[number];
export const WORK_MODE_LABELS: Record<WorkMode, string> = { OFFICE: 'Escritório', REMOTE: 'Remoto', TRAVEL: 'Viagem' };
export function workModeLabel(mode: WorkMode | null | undefined): string { return mode ? WORK_MODE_LABELS[mode] : 'Não informado'; }
export interface MarkingCommand { type: EventType; expectedWorkSessionId: string | null; timeZoneId?: string; workMode?: WorkMode; }
export interface Attempt extends MarkingCommand { version: 1; requestKey: string; userId: string; employeeId: string; }
export interface Receipt {
  requestKey: string; type: EventType; httpStatus: number; code: string;
  workSessionId: string | null; eventId: string | null; occurredAtUtc: string | null; confirmedAtUtc: string;
}
export interface CurrentJourney {
  session: {
    id: string; state: 'WORKING' | 'PAUSED' | 'CLOSED'; workDate: string; referenceTimeZoneId: string;
    openedAtUtc: string; lastEvent: { id: string; type: EventType; occurredAtUtc: string; recordedTimeZoneId: string; workMode?: WorkMode | null };
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
