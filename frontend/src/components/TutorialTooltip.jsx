import { useEffect, useId, useRef } from 'react';

export default function TutorialTooltip({ index, step, backProps, primaryProps, skipProps, isLastStep, size, tooltipProps }) {
    const id = useId();
    const containerRef = useRef(null);
    const headingRef = useRef(null);
    useEffect(() => {
        headingRef.current?.focus({ preventScroll: true });
        const containFocus = event => {
            if (containerRef.current && !containerRef.current.contains(event.target)) headingRef.current?.focus({ preventScroll: true });
        };
        document.addEventListener('focusin', containFocus);
        return () => document.removeEventListener('focusin', containFocus);
    }, [index]);

    const handleTab = event => {
        if (event.key !== 'Tab') return;
        const buttons = [...containerRef.current.querySelectorAll('button:not([disabled])')];
        const focused = buttons.indexOf(document.activeElement);
        if (focused === -1 || (event.shiftKey && focused === 0) || (!event.shiftKey && focused === buttons.length - 1)) {
            event.preventDefault();
            (event.shiftKey ? buttons.at(-1) : buttons[0])?.focus();
        }
    };

    return <div ref={containerRef} onKeyDown={handleTab} {...tooltipProps} role="dialog" aria-labelledby={`${id}-title`} aria-describedby={`${id}-content`}
        className="tutorial-tooltip rounded-4 shadow-lg p-4">
        <h2 ref={headingRef} tabIndex={-1} id={`${id}-title`} className="h5 app-accent-text mb-3">{step.title || 'Az oldal bemutatása'}</h2>
        <div id={`${id}-content`}>{step.content}</div>
        <div className="d-flex flex-wrap justify-content-between align-items-center gap-3 mt-4 pt-3 border-top">
            <div className="d-flex align-items-center gap-3">
                {!isLastStep && <button {...skipProps} type="button" className="btn btn-link app-accent-link p-1">Kihagyás</button>}
                <span className="small" aria-label={`${size} lépésből ${index + 1}. lépés`}>{index + 1} / {size}</span>
            </div>
            <div className="d-flex flex-wrap gap-2">
                {index > 0 && <button {...backProps} type="button" className="btn btn-sm btn-outline-info fw-bold">Vissza</button>}
                <button {...primaryProps} type="button" className="btn btn-sm btn-info fw-bold text-dark">
                    {isLastStep ? 'Készen vagyok!' : 'Tovább'}
                </button>
            </div>
        </div>
    </div>;
}
