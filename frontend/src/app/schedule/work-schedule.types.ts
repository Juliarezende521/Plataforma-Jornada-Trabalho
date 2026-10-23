export interface WorkSchedule { id:string; employeeId:string; effectiveFrom:string; minutes:number[]; toleranceMinutes:number; createdAtUtc:string; }
export interface WorkScheduleInput { effectiveFrom:string; mondayMinutes:number; tuesdayMinutes:number; wednesdayMinutes:number; thursdayMinutes:number; fridayMinutes:number; saturdayMinutes:number; sundayMinutes:number; toleranceMinutes:number; }
export interface WorkSchedulePage { items:WorkSchedule[]; page:number; size:number; totalElements:number; }
export interface ScheduleException { id:string; employeeId:string; workDate:string; plannedMinutes:number; toleranceMinutes:number; category:'HOLIDAY'|'TIME_OFF'|'TRAINING'|'SPECIAL'; reason:string; createdAtUtc:string; }
export interface ScheduleExceptionPage { items:ScheduleException[]; page:number; size:number; totalElements:number; }
export type ScheduleExceptionInput = Pick<ScheduleException, 'workDate'|'plannedMinutes'|'toleranceMinutes'|'category'|'reason'>;
