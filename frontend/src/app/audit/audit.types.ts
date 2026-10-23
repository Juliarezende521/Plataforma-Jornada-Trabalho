export interface AuditEntry {
  id: string;
  actorUserId: string;
  actorEmail: string | null;
  action: string;
  entityType: string;
  entityId: string;
  recordedAtUtc: string;
  details: string | null;
}

export interface AuditPage { items: AuditEntry[]; page: number; size: number; totalElements: number; }
