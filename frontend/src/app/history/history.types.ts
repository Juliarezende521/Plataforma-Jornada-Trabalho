import { EventType, WorkMode } from '../journey/journey.types';
export interface HistoryItem {
  id: string; state: 'WORKING' | 'PAUSED' | 'CLOSED'; workDate: string; referenceTimeZoneId: string;
  openedAtUtc: string; closedAtUtc: string | null;
}
export interface HistoryPage {
  items: HistoryItem[]; page: number; size: number; totalElements: number; observedAtUtc: string;
}
export interface PeriodSummary {
  from:string; to:string; totalWorkDuration:string; plannedWorkDuration:string; balanceSeconds:number|null; toleranceSeconds:number|null; withinTolerance:boolean|null; scheduledDays:number; periodDays:number; totalSessions:number; closedSessions:number;
  openSessions:number; adjustedSessions:number; workedDays:number; observedAtUtc:string;
}
export interface JourneyDetail {
  session: HistoryItem; observedAtUtc: string;
  events: { id: string; sequence: number; type: EventType; occurredAtUtc: string; recordedAtUtc: string; recordedTimeZoneId: string; workMode?: WorkMode | null; correction?:boolean }[];
  effective?: { state: 'WORKING'|'PAUSED'|'CLOSED'; workDuration: string; adjusted: boolean;
    events: { source: 'ORIGINAL'|'ADJUSTMENT'; originalEventId: string|null; adjustmentId: string|null; originalSequence?:number|null;
      type: EventType; occurredAtUtc: string; recordedTimeZoneId: string|null; workMode?: WorkMode|null }[] };
}
export interface HistoryFilter { from: string; to: string; }

export interface EmployeeSummary { id: string; name: string; referenceTimeZoneId: string; }
export interface EmployeePage { items: EmployeeSummary[]; page: number; size: number; totalElements: number; }
