import { useCallback, useEffect, useRef, useState } from 'react';
import { assignmentApi } from '../services/assignmentApi';

/** Serialize writes and reject stale tabs rather than silently overwriting answers. */
export default function useAssignmentDraft({ sessionId, snapshot, revision, initialDraft, enabled, onFinished }) {
    const latest = useRef(snapshot);
    const accepted = useRef(JSON.stringify(initialDraft || snapshot));
    const version = useRef(revision);
    const inFlight = useRef(null);
    const blocked = useRef(false);
    const alive = useRef(true);
    const finish = useRef(onFinished);
    const [status, setStatus] = useState('saved');
    const [savedSignature, setSavedSignature] = useState(() => JSON.stringify(initialDraft || snapshot));
    const [error, setError] = useState('');
    const [conflict, setConflict] = useState(false);

    useEffect(() => { alive.current = true; return () => { alive.current = false; }; }, []);
    useEffect(() => { latest.current = snapshot; finish.current = onFinished; }, [snapshot, onFinished]);

    const flush = useCallback(async () => {
        if (inFlight.current) await inFlight.current;
        if (blocked.current) return { ok: false };
        const value = latest.current;
        const signature = JSON.stringify(value);
        if (signature === accepted.current) return { ok: true, revision: version.current };
        const write = async () => {
            if (alive.current) setStatus('saving');
            try {
                const { data } = await assignmentApi.saveDraft(sessionId, { ...value, revision: version.current });
                version.current = data.revision;
                accepted.current = signature;
                if (alive.current) {
                    setSavedSignature(signature);
                    setStatus('saved');
                    setError('');
                    if (data.timing?.finished) finish.current();
                }
                return { ok: true, finished: data.timing?.finished, revision: version.current };
            } catch (err) {
                if (err.response?.status === 409) blocked.current = true;
                if (alive.current) {
                    setConflict(blocked.current);
                    setStatus('error');
                    setError(blocked.current
                        ? 'Másik ablak módosította ezt a próbálkozást. Az itteni nem mentett változtatások nem írhatják felül a szerveres állapotot.'
                        : 'A legutóbbi változtatások még nincsenek a szerveren. Tartsd nyitva ezt az oldalt; a kapcsolat helyreállásakor újrapróbáljuk.');
                }
                return { ok: false };
            } finally { inFlight.current = null; }
        };
        inFlight.current = write();
        return inFlight.current;
    }, [sessionId]);

    useEffect(() => {
        if (!enabled || blocked.current || JSON.stringify(snapshot) === accepted.current) return;
        const timer = setTimeout(() => { void flush(); }, 500);
        return () => clearTimeout(timer);
    }, [snapshot, enabled, flush]);

    useEffect(() => {
        if (!enabled) return;
        const retry = () => { void flush(); };
        const timer = setInterval(retry, 2000);
        const warn = event => {
            if (inFlight.current || JSON.stringify(latest.current) !== accepted.current) {
                event.preventDefault(); event.returnValue = '';
            }
        };
        window.addEventListener('online', retry);
        window.addEventListener('beforeunload', warn);
        return () => { clearInterval(timer); window.removeEventListener('online', retry); window.removeEventListener('beforeunload', warn); };
    }, [enabled, flush]);

    const markConflict = () => {
        blocked.current = true; setConflict(true); setStatus('error');
        setError('Másik ablak módosította ezt a próbálkozást. Töltsd be a szerveren mentett állapotot.');
    };
    return { status: status === 'saved' && JSON.stringify(snapshot) !== savedSignature ? 'pending' : status,
        error, conflict, flush, markConflict };
}
