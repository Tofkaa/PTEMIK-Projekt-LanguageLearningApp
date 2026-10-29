/**
 * @file viewedStore.js
 * @description Felhasználóhoz kötött (userId-scoped) olvasottság-kezelő és szinkronizáló segédmodul.
 */

const getScopedKey = (baseKey, userId) => {
    if (!userId) return null;
    return `${baseKey}:${userId}`;
};

export const getViewedIds = (baseKey, userId) => {
    const key = getScopedKey(baseKey, userId);
    if (!key) return [];
    try {
        return JSON.parse(localStorage.getItem(key) || '[]');
    } catch {
        return [];
    }
};

export const getUnseenIds = (baseKey, userId, serverIds = []) => {
    if (!userId || !Array.isArray(serverIds) || serverIds.length === 0) return [];
    const viewed = new Set(getViewedIds(baseKey, userId));
    return serverIds.filter(id => !viewed.has(String(id)));
};

export const markAsViewed = (baseKey, userId, idsToMark = []) => {
    const key = getScopedKey(baseKey, userId);
    if (!key || !Array.isArray(idsToMark) || idsToMark.length === 0) return;

    const stringIds = idsToMark.map(id => String(id));
    const current = getViewedIds(baseKey, userId);
    const updated = [...new Set([...current, ...stringIds])];

    if (updated.length !== current.length) {
        localStorage.setItem(key, JSON.stringify(updated));
        window.dispatchEvent(new Event('local-storage-update'));
    }
};

/**
 * Szinkronizálja a tárolt ID-kat a szerver aktuális listájával:
 * eltávolítja azokat az ID-kat, amelyek már nem szerepelnek a szerver válaszában
 * (pl. időközben leosztályozott beadás, elbírált csatlakozási kérelem vagy lejárt feladat).
 */
export const pruneViewedStore = (userId, summaryData) => {
    if (!userId || !summaryData) return;

    const mappings = [
        ['viewedAssignments', summaryData.studentActiveAssignmentIds],
        ['viewedResults', summaryData.studentGradedSessionIds],
        ['viewedTeacherPending', summaryData.teacherPendingJoinRequestIds],
        ['viewedTeacherUngraded', summaryData.teacherUngradedSubmissionIds]
    ];

    let changed = false;

    mappings.forEach(([baseKey, serverIds]) => {
        if (!Array.isArray(serverIds)) return;
        const key = getScopedKey(baseKey, userId);
        const current = getViewedIds(baseKey, userId);
        const serverSet = new Set(serverIds.map(String));
        const pruned = current.filter(id => serverSet.has(String(id)));

        if (pruned.length !== current.length) {
            localStorage.setItem(key, JSON.stringify(pruned));
            changed = true;
        }
    });

    if (changed) {
        window.dispatchEvent(new Event('local-storage-update'));
    }
};

export const getScopedCounter = (baseKey, userId) => {
    const key = getScopedKey(baseKey, userId);
    if (!key) return 0;
    return parseInt(localStorage.getItem(key) || '0', 10);
};

export const setScopedCounter = (baseKey, userId, value) => {
    const key = getScopedKey(baseKey, userId);
    if (!key || value == null) return;
    localStorage.setItem(key, String(value));
    window.dispatchEvent(new Event('local-storage-update'));
};
export const vocabularyViewedScope = user => user?.userId ? `${user.userId}:${user.activeCourseId || user.activeCourseCode || "none"}` : null;
