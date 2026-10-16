import { useEffect, useState } from 'react';

/** Keeps time-based UI state current, including after background tab throttling. */
export const useCurrentTime = () => {
    const [currentTime, setCurrentTime] = useState(() => Date.now());

    useEffect(() => {
        const updateTime = () => setCurrentTime(Date.now());
        const intervalId = window.setInterval(updateTime, 1000);
        window.addEventListener('focus', updateTime);
        document.addEventListener('visibilitychange', updateTime);

        return () => {
            window.clearInterval(intervalId);
            window.removeEventListener('focus', updateTime);
            document.removeEventListener('visibilitychange', updateTime);
        };
    }, []);

    return currentTime;
};
