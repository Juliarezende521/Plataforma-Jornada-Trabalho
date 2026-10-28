export interface CountryWorkPolicy {
  id: string;
  countryCode: string;
  effectiveFrom: string;
  standardWeeklyMinutes: number;
  maximumDailyMinutes: number;
  minimumBreakMinutes: number;
  overtimeToleranceMinutes: number;
  createdAtUtc: string;
}
export interface CountryWorkPolicyPage {
  items: CountryWorkPolicy[];
  page: number;
  size: number;
  totalElements: number;
}
export type CountryWorkPolicyInput = Omit<CountryWorkPolicy, 'id' | 'createdAtUtc'>;
