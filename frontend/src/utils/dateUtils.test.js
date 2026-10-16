import { describe, expect, it } from 'vitest';
import { formatToLocalDisplay, formatToLocalTimestamp, formatToUtcForServer, getRemainingTimeMs, parseServerDate } from './dateUtils';

describe('server date parsing during UTC migration', () => {
    it.each([
        '2026-10-16T16:00:00Z',
        '2026-10-16T18:00:00+02:00',
        '2026-10-16T11:00:00-05:00',
        '2026-10-16T16:00:00',
        [2026, 10, 16, 16, 0, 0],
    ])('preserves the same instant for %j', (value) => {
        expect(parseServerDate(value).toISOString()).toBe('2026-10-16T16:00:00.000Z');
    });

    it('preserves milliseconds from legacy Jackson nanoseconds', () => {
        expect(parseServerDate([2026, 10, 16, 16, 0, 0, 123456789]).getUTCMilliseconds()).toBe(123);
    });

    it.each([null, '', 'invalid'])('handles missing or invalid dates: %j', (value) => {
        expect(parseServerDate(value)).toBeNull();
        expect(formatToLocalDisplay(value)).toBe('N/A');
        expect(formatToLocalTimestamp(value)).toBe('N/A');
        expect(getRemainingTimeMs(value, 30)).toBeNull();
    });
});

describe('local datetime input', () => {
    it.each(['2026-01-16T18:00', '2026-07-16T18:00'])('round-trips local wall time in winter and summer: %s', (value) => {
        const sent = formatToUtcForServer(value);
        expect(sent.endsWith('Z')).toBe(true);
        const received = parseServerDate(sent);
        const local = new Date(value);
        expect(received.getTime()).toBe(local.getTime());
        expect(received.getHours()).toBe(18);
        expect(received.getDate()).toBe(16);
    });

    it('keeps a missing optional deadline empty', () => {
        expect(formatToUtcForServer('')).toBeNull();
    });

    it.each(['invalid', '2026-02-30T10:00', '2026-10-16T25:00'])('rejects invalid local input: %s', value => {
        expect(() => formatToUtcForServer(value)).toThrow(RangeError);
    });
});

const zone = Intl.DateTimeFormat().resolvedOptions().timeZone;
const zoneCases = {
    'Europe/Budapest': {
        winter: '2026-01-16T17:00:00.000Z', summer: '2026-07-16T16:00:00.000Z',
        gap: '2026-03-29T02:30', fold: '2026-10-25T02:30', foldUtc: '2026-10-25T00:30:00.000Z',
    },
    'America/New_York': {
        winter: '2026-01-16T23:00:00.000Z', summer: '2026-07-16T22:00:00.000Z',
        gap: '2026-03-08T02:30', fold: '2026-11-01T01:30', foldUtc: '2026-11-01T05:30:00.000Z',
    },
}[zone];

describe.skipIf(!zoneCases)(`explicit timezone expectations: ${zone}`, () => {
    it('converts winter and summer offsets correctly', () => {
        expect(formatToUtcForServer('2026-01-16T18:00')).toBe(zoneCases.winter);
        expect(formatToUtcForServer('2026-07-16T18:00')).toBe(zoneCases.summer);
    });

    it('rejects the nonexistent spring local time', () => {
        expect(() => formatToUtcForServer(zoneCases.gap)).toThrow(RangeError);
    });

    it('uses the earlier occurrence of a repeated autumn local time', () => {
        expect(formatToUtcForServer(zoneCases.fold)).toBe(zoneCases.foldUtc);
    });

    it('formats the same offset-bearing instant consistently', () => {
        expect(formatToLocalTimestamp('2026-10-16T18:00:00+02:00'))
            .toBe(formatToLocalTimestamp('2026-10-16T16:00:00Z'));
    });
});
