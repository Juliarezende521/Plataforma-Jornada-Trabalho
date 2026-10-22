export interface WorkSchedule { id:string; employeeId:string; effectiveFrom:string; minutes:number[]; createdAtUtc:string; }
export interface WorkScheduleInput { effectiveFrom:string; mondayMinutes:number; tuesdayMinutes:number; wednesdayMinutes:number; thursdayMinutes:number; fridayMinutes:number; saturdayMinutes:number; sundayMinutes:number; }
