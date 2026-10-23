export interface WorkSchedule { id:string; employeeId:string; effectiveFrom:string; minutes:number[]; toleranceMinutes:number; createdAtUtc:string; }
export interface WorkScheduleInput { effectiveFrom:string; mondayMinutes:number; tuesdayMinutes:number; wednesdayMinutes:number; thursdayMinutes:number; fridayMinutes:number; saturdayMinutes:number; sundayMinutes:number; toleranceMinutes:number; }
export interface WorkSchedulePage { items:WorkSchedule[]; page:number; size:number; totalElements:number; }
