import { useRef } from 'react';
import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import TimedSuccessFeedback from './TimedSuccessFeedback';

let hidden, dismiss;
beforeEach(() => {
    vi.useFakeTimers({ toFake: ['setTimeout', 'clearTimeout', 'performance'] });
    hidden = vi.spyOn(document, 'hidden', 'get').mockReturnValue(false);
    vi.stubGlobal('IntersectionObserver', undefined);
    dismiss = vi.fn();
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); vi.unstubAllGlobals(); });
const advance = ms => act(() => vi.advanceTimersByTime(ms));

it('keeps the polite live region mounted before, during and after a success message', () => {
    const view = render(<TimedSuccessFeedback message={null} onDismiss={dismiss} />);
    const region = view.container.querySelector('[aria-live="polite"]');
    view.rerender(<TimedSuccessFeedback message={{ id: 1, text: 'Elfogadva.' }} onDismiss={dismiss} />);
    expect(screen.getByRole('status')).toBe(region);
    expect(region).toHaveTextContent('Elfogadva.');
    view.rerender(<TimedSuccessFeedback message={null} onDismiss={dismiss} />);
    expect(view.container.querySelector('[aria-live="polite"]')).toBe(region);
    expect(region).toBeEmptyDOMElement();
});

it('dismisses after ten visible seconds without taking focus', () => {
    render(<><button>Másik művelet</button><TimedSuccessFeedback message={{ id: 1, text: 'Elfogadva.' }} onDismiss={dismiss} /></>);
    screen.getByRole('button', { name: 'Másik művelet' }).focus();
    advance(9999); expect(dismiss).not.toHaveBeenCalled();
    advance(1); expect(dismiss).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('button', { name: 'Másik művelet' })).toHaveFocus();
    expect(screen.getByRole('status')).toHaveAttribute('aria-live', 'polite');
});
it('pauses on hover and continues with the remaining duration', () => {
    render(<TimedSuccessFeedback message={{ id: 1, text: 'Elfogadva.' }} onDismiss={dismiss} />);
    advance(4000); fireEvent.mouseEnter(screen.getByText('Elfogadva.').closest('.alert'));
    advance(30000); expect(dismiss).not.toHaveBeenCalled();
    fireEvent.mouseLeave(screen.getByText('Elfogadva.').closest('.alert'));
    advance(5999); expect(dismiss).not.toHaveBeenCalled();
    advance(1); expect(dismiss).toHaveBeenCalledTimes(1);
});
it('keeps focused feedback and restores the list heading when manually closed', () => {
    function Example() {
        const heading = useRef(null);
        return <><h2 ref={heading} tabIndex={-1}>Beérkező kérelmek</h2>
            <TimedSuccessFeedback message={{ id: 1, text: 'Elfogadva.' }} onDismiss={dismiss} focusTarget={heading} /></>;
    }
    render(<Example />);
    const close = screen.getByRole('button', { name: 'Sikerüzenet bezárása' });
    act(() => close.focus());
    advance(30000); expect(dismiss).not.toHaveBeenCalled();
    fireEvent.click(close); expect(dismiss).toHaveBeenCalledTimes(1);
    expect(screen.getByRole('heading')).toHaveFocus();
});
it('pauses while the browser tab is hidden', () => {
    render(<TimedSuccessFeedback message={{ id: 1, text: 'Elfogadva.' }} onDismiss={dismiss} />);
    advance(3000); hidden.mockReturnValue(true); fireEvent(document, new Event('visibilitychange'));
    advance(30000); expect(dismiss).not.toHaveBeenCalled();
    hidden.mockReturnValue(false); fireEvent(document, new Event('visibilitychange'));
    advance(7000); expect(dismiss).toHaveBeenCalledTimes(1);
});
it('does not count time while a tab pane or the message is outside the visible area', () => {
    let intersection;
    vi.stubGlobal('IntersectionObserver', class {
        constructor(callback) { intersection = callback; }
        observe() {}
        disconnect() {}
    });
    const view = render(<TimedSuccessFeedback message={{ id: 1, text: 'Elfogadva.' }} onDismiss={dismiss} />);
    advance(30000); expect(dismiss).not.toHaveBeenCalled();
    act(() => intersection([{ isIntersecting: true }])); advance(4000);
    act(() => intersection([{ isIntersecting: false }])); advance(30000);
    expect(dismiss).not.toHaveBeenCalled();
    act(() => intersection([{ isIntersecting: true }])); advance(6000);
    expect(dismiss).toHaveBeenCalledTimes(1);
    view.unmount(); advance(30000); expect(dismiss).toHaveBeenCalledTimes(1);
});
