/**
 * @file dateUtils.js
 * @description Centralized utility for handling dates and timezones securely.
 * Bridges the gap between the server's UTC timestamps and the client's local timezone,
 * preventing synchronization glitches even if the user travels across timezones.
 */

/**
 * Parses a server timestamp into a Date representing the same instant.
 * Local timezone conversion happens only when formatting for display.
 * 
 * @param {Array|string} dateData - The raw date payload from the Spring Boot backend.
 * @returns {Date|null} The represented instant, or null for missing/invalid data.
 */
export const parseServerDate = (dateData) => {
    if (!dateData) return null;
    
    // Fallback for older array-based Jackson serialization
    if (Array.isArray(dateData)) {
        const date = new Date(Date.UTC(dateData[0], dateData[1] - 1, dateData[2], dateData[3] || 0, dateData[4] || 0, dateData[5] || 0, Math.floor((dateData[6] || 0) / 1_000_000)));
        return Number.isNaN(date.getTime()) ? null : date;
    }
    
    // Standard ISO-8601 parsing
    const str = String(dateData).trim();
    // Older endpoints still return timezone-less UTC values during migration.
    const hasTimeZone = /(?:Z|[+-]\d{2}:?\d{2})$/i.test(str);
    const date = new Date(hasTimeZone ? str : str + 'Z');
    return Number.isNaN(date.getTime()) ? null : date;
};

/**
 * Formats a server date into a human-readable local string (Hungarian locale).
 * 
 * @param {Array|string} dateData - The raw date from the backend.
 * @param {boolean} [includeTime=true] - Whether to include hours and minutes.
 * @returns {string} Formatted string (e.g., "aug. 21. 11:12" or "2026. augusztus 21.").
 */
export const formatToLocalDisplay = (dateData, includeTime = true) => {
    const date = parseServerDate(dateData);
    if (!date) return "N/A";
    
    const options = includeTime 
        ? { month: 'short', day: 'numeric', hour: '2-digit', minute: '2-digit' }
        : { year: 'numeric', month: 'long', day: 'numeric' };
        
    return date.toLocaleString('hu-HU', options);
};

/** Full local date and time, including seconds, for logs and activity details. */
export const formatToLocalTimestamp = (dateData) => {
    const date = parseServerDate(dateData);
    return date ? date.toLocaleString('hu-HU') : 'N/A';
};

/** Value for a minute-precision datetime-local field, expressed in the browser's timezone. */
export const formatToLocalInput = (dateData) => {
    const date = parseServerDate(dateData);
    if (!date) return '';
    const pad = value => String(value).padStart(2, '0');
    return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};

/**
 * Converts a local datetime-local input string to a standard UTC ISO string.
 * Used exclusively before API POST/PUT requests.
 * 
 * @param {string} localDateStr - The local date string from an HTML input.
 * @returns {string|null} The UTC string payload for the backend.
 */
export const formatToUtcForServer = (localDateStr) => {
    if (!localDateStr) return null;
    const parts = /^(\d{4})-(\d{2})-(\d{2})T(\d{2}):(\d{2})(?::(\d{2})(?:\.(\d{1,3}))?)?$/.exec(localDateStr);
    if (!parts) throw new RangeError('Érvénytelen helyi dátum vagy időpont.');
    const localDate = new Date(localDateStr);
    const expected = parts.slice(1, 7).map(value => Number(value || 0));
    const actual = [localDate.getFullYear(), localDate.getMonth() + 1, localDate.getDate(),
        localDate.getHours(), localDate.getMinutes(), localDate.getSeconds()];
    // Reject impossible dates and spring DST gaps rather than silently moving them.
    if (actual.some((value, index) => value !== expected[index])) {
        throw new RangeError('Ez a helyi időpont nem létezik. Ellenőrizd a dátumot és az óraátállítást.');
    }
    return localDate.toISOString();
};

/**
 * Calculates remaining time using epoch milliseconds. Timezone-independent,
 * but dependent on the client's system clock; the server applies submission policy.
 * 
 * @param {Array|string} serverStartedAt - UTC start time of the session.
 * @param {number} timeLimitMinutes - The allowed duration in minutes.
 * @returns {number|null} Remaining time in milliseconds.
 */
export const getRemainingTimeMs = (serverStartedAt, timeLimitMinutes) => {
    if (!serverStartedAt || !timeLimitMinutes) return null;
    
    const limitMs = timeLimitMinutes * 60 * 1000;
    const startDate = parseServerDate(serverStartedAt);
    if (!startDate) return null;
    const startMs = startDate.getTime(); 
    
    return Math.max(0, limitMs - (Date.now() - startMs));
};
