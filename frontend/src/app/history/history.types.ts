import { EventType } from '../journey/journey.types';
export interface HistoryItem {
  id: string; state: 'WORKING' | 'PAUSED' | 'CLOSED'; workDate: string; referenceTimeZoneId: string;
  openedAtUtc: string; closedAtUtc: string | null; workDuration: string; provisional: boolean;
}
export interface HistoryPage {
  items: HistoryItem[]; page: number; size: number; totalElements: number; observedAtUtc: string;
  summary: { closedWorkDuration: string; closedCount: number; pendingCount: number };
}
export interface JourneyDetail {
  session: HistoryItem; observedAtUtc: string;
  events: { id: string; sequence: number; type: EventType; occurredAtUtc: string; recordedAtUtc: string }[];
  intervals: { kind: 'WORK' | 'PAUSE'; startAtUtc: string; endAtUtc: string; duration: string; provisional: boolean }[];
}
export interface HistoryFilter { from: string; to: string; }
