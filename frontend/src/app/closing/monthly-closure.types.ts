export interface MonthlyClosure {
  id: string;
  employeeId: string;
  competence: string;
  periodFrom: string;
  periodTo: string;
  totalWorkSeconds: number;
  plannedWorkSeconds: number;
  balanceSeconds: number;
  toleranceSeconds: number;
  withinTolerance: boolean;
  totalSessions: number;
  adjustedSessions: number;
  workedDays: number;
  status: 'CLOSED';
  closedAtUtc: string;
}

export interface MonthlyClosurePage {
  items: MonthlyClosure[];
  page: number;
  size: number;
  totalElements: number;
}
