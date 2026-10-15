import { Injectable } from '@angular/core';
import { Identity } from '../auth/auth.service';
import { Attempt, EVENT_TYPES, MarkingCommand } from './journey.types';

const PREFIX = 'work-journey.online.v1.';
const UUID = /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i;

/** Minimal recovery diary. Its contents never authorize an API operation. */
@Injectable({ providedIn: 'root' })
export class AttemptDiary {
  list(identity: Identity): Attempt[] {
    const attempts: Attempt[] = [];
    for (let index = 0; index < localStorage.length; index++) {
      const key = localStorage.key(index);
      if (!key?.startsWith(PREFIX)) continue;
      const value: unknown = JSON.parse(localStorage.getItem(key) ?? 'null');
      if (!this.valid(value) || key !== PREFIX + value.requestKey) throw new Error('Invalid attempt diary');
      if (value.userId === identity.userId && value.employeeId === identity.employeeId) attempts.push(value);
    }
    return attempts.sort((a, b) => a.requestKey.localeCompare(b.requestKey));
  }

  save(identity: Identity, command: MarkingCommand): Attempt {
    const attempt: Attempt = { version: 1, requestKey: crypto.randomUUID(), userId: identity.userId,
      employeeId: identity.employeeId, type: command.type, expectedWorkSessionId: command.expectedWorkSessionId };
    if (command.timeZoneId !== undefined) attempt.timeZoneId = command.timeZoneId;
    if (!this.valid(attempt)) throw new Error('Invalid identity or command');
    const serialized = JSON.stringify(attempt);
    const key = PREFIX + attempt.requestKey;
    localStorage.setItem(key, serialized);
    if (localStorage.getItem(key) !== serialized) throw new Error('Attempt could not be preserved');
    return attempt;
  }

  remove(attempt: Attempt): void {
    const key = PREFIX + attempt.requestKey;
    const raw = localStorage.getItem(key);
    // A changed entry must be reviewed rather than silently removed by another tab.
    if (raw !== null && JSON.stringify(JSON.parse(raw)) !== JSON.stringify(attempt)) throw new Error('Attempt changed');
    localStorage.removeItem(key);
    if (localStorage.getItem(key) !== null) throw new Error('Attempt could not be removed');
  }

  isDiaryEvent(event: StorageEvent): boolean { return event.key === null || event.key.startsWith(PREFIX); }

  private valid(value: unknown): value is Attempt {
    if (typeof value !== 'object' || value === null) return false;
    const item = value as Record<string, unknown>;
    const fields = ['version', 'requestKey', 'userId', 'employeeId', 'type', 'expectedWorkSessionId'];
    if (!fields.every(key => Object.hasOwn(item, key)) ||
        Object.keys(item).some(key => !fields.includes(key) && key !== 'timeZoneId')) return false;
    if (Object.hasOwn(item, 'timeZoneId') &&
        (typeof item['timeZoneId'] !== 'string' || item['timeZoneId'].length === 0 || item['timeZoneId'].length > 100)) return false;
    return item['version'] === 1 && ['requestKey', 'userId', 'employeeId'].every(key =>
      typeof item[key] === 'string' && UUID.test(item[key])) &&
      EVENT_TYPES.some(type => type === item['type']) &&
      (item['type'] === 'ENTRY' ? item['expectedWorkSessionId'] === null :
        typeof item['expectedWorkSessionId'] === 'string' && UUID.test(item['expectedWorkSessionId']));
  }
}
