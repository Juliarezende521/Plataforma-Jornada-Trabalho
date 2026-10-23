export interface EmploymentContract {
  id: string;
  employeeId: string;
  effectiveFrom: string;
  countryCode: string;
  employmentType: 'FULL_TIME' | 'PART_TIME';
  weeklyMinutes: number;
  createdAtUtc: string;
}
export interface EmploymentContractPage {
  items: EmploymentContract[];
  page: number;
  size: number;
  totalElements: number;
}
export type EmploymentContractInput = Pick<
  EmploymentContract,
  'effectiveFrom' | 'countryCode' | 'employmentType' | 'weeklyMinutes'
>;
