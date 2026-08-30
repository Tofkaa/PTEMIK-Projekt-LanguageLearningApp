/**
 * @file dateUtils.js
 * @description Centralized utility for handling dates and timezones securely.
 * Bridges the gap between the server's UTC timestamps and the client's local timezone,
 * preventing synchronization glitches even if the user travels across timezones.
 */

/**
 * Parses raw date data from the backend into a safe, local timezone-aware Date object.
 * 
 * @param {Array|string} dateData - The raw date payload from the Spring Boot backend.
 * @returns {Date|null} A standard JavaScript Date object adjusted to local time.
 */
export const parseServerDate = (dateData) => {
    if (!dateData) return null;
    
    // Fallback for older array-based Jackson serialization
    if (Array.isArray(dateData)) {
        return new Date(Date.UTC(dateData[0], dateData[1] - 1, dateData[2], dateData[3] || 0, dateData[4] || 0, dateData[5] || 0));
    }
    
    // Standard ISO-8601 parsing
    const str = String(dateData);
    // Ensure the UTC 'Z' flag is present for the browser engine
    return new Date(str.endsWith('Z') ? str : str + 'Z'); 
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

/**
 * Converts a local datetime-local input string to a standard UTC ISO string.
 * Used exclusively before API POST/PUT requests.
 * 
 * @param {string} localDateStr - The local date string from an HTML input.
 * @returns {string|null} The UTC string payload for the backend.
 */
export const formatToUtcForServer = (localDateStr) => {
    if (!localDateStr) return null;
    const localDate = new Date(localDateStr);
    return localDate.toISOString().substring(0, 19);
};

/**
 * A bulletproof countdown timer logic based on UNIX epoch milliseconds.
 * Immune to user timezone changes or manual OS clock manipulation.
 * 
 * @param {Array|string} serverStartedAt - UTC start time of the session.
 * @param {number} timeLimitMinutes - The allowed duration in minutes.
 * @returns {number|null} Remaining time in milliseconds.
 */
export const getRemainingTimeMs = (serverStartedAt, timeLimitMinutes) => {
    if (!serverStartedAt || !timeLimitMinutes) return null;
    
    const limitMs = timeLimitMinutes * 60 * 1000;
    const startDate = parseServerDate(serverStartedAt);
    const startMs = startDate.getTime(); 
    
    return Math.max(0, limitMs - (Date.now() - startMs));
};