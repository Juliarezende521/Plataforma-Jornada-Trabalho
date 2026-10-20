import { EventType } from '../journey/journey.types';

export type AdjustmentStatus = 'PENDING' | 'APPROVED' | 'REJECTED';
export interface WorkAdjustment {
  id: string;
  workSessionId: string;
  employeeId: string;
  employeeName: string;
  requestedEventType: EventType;
  proposedOccurredAtUtc: string;
  reason: string;
  status: AdjustmentStatus;
  requestedAtUtc: string;
  decidedAtUtc: string | null;
  decisionNote: string | null;
}
export interface AdjustmentPage { items: WorkAdjustment[]; page: number; size: number; totalElements: number; }
