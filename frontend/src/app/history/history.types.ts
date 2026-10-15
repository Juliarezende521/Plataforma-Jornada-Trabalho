import { EventType } from '../journey/journey.types';
export interface HistoryItem {
  id: string; state: 'WORKING' | 'PAUSED' | 'CLOSED'; workDate: string; referenceTimeZoneId: string;
  openedAtUtc: string; closedAtUtc: string | null;
}
export interface HistoryPage {
  items: HistoryItem[]; page: number; size: number; totalElements: number; observedAtUtc: string;
}
export interface JourneyDetail {
  session: HistoryItem; observedAtUtc: string;
  events: { id: string; sequence: number; type: EventType; occurredAtUtc: string; recordedAtUtc: string; recordedTimeZoneId: string }[];
}
export interface HistoryFilter { from: string; to: string; }

export interface EmployeeSummary { id: string; name: string; referenceTimeZoneId: string; }
export interface EmployeePage { items: EmployeeSummary[]; page: number; size: number; totalElements: number; }
