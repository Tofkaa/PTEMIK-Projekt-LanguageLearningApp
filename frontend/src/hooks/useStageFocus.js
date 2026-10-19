import { useEffect, useRef } from 'react';

/** Announce a new question/result through focus, without reacting to typing or timers. */
export default function useStageFocus(stage) {
    const headingRef = useRef(null);
    useEffect(() => {
        headingRef.current?.focus();
    }, [stage]);
    return headingRef;
}
