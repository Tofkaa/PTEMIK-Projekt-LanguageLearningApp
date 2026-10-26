import { useEffect, useState } from 'react';
import axios from 'axios';
import api from '../services/api';

/** A replaced account/course view must never consume an earlier view's response. */
export default function useScopedData(url, scope, enabled = true) {
    const [result, setResult] = useState(null);
    useEffect(() => {
        if (!enabled) return;
        const controller = new AbortController();
        let active = true;
        api.get(url, { signal: controller.signal }).then(response => {
            if (active) setResult({ scope, url, data: response.data, error: null });
        }).catch(error => {
            if (active && !axios.isCancel(error)) setResult({ scope, url, data: [], error });
        });
        return () => { active = false; controller.abort(); };
    }, [url, scope, enabled]);

    if (!enabled || result?.scope !== scope || result?.url !== url) {
        return { data: [], isLoading: enabled, error: null };
    }
    return { data: result.data, isLoading: false, error: result.error };
}
