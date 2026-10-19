import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { afterEach, describe, expect, it, vi } from 'vitest';
import GlobalErrorToast from '../GlobalErrorToast';

afterEach(() => { cleanup(); vi.useRealTimers(); });

function emitError(message) {
    act(() => window.dispatchEvent(new CustomEvent('api-error', { detail: message })));
}

describe('persistent accessible error toast', () => {
    it('keeps an error visible until explicitly dismissed without stealing focus', async () => {
        render(<><button>Újrapróbálás</button><GlobalErrorToast /></>);
        const origin = screen.getByRole('button', { name: 'Újrapróbálás' });
        origin.focus();
        emitError('Nem sikerült a mentés.');
        await screen.findByRole('button', { name: 'Hibaüzenet bezárása' });
        expect(origin).toHaveFocus();
        vi.useFakeTimers();
        act(() => vi.advanceTimersByTime(60_000));
        expect(screen.getByRole('alert')).toHaveTextContent('Nem sikerült a mentés.');
        vi.useRealTimers();
        const close = screen.getByRole('button', { name: 'Hibaüzenet bezárása' });
        close.focus();
        fireEvent.click(close);
        expect(origin).toHaveFocus();
        await waitFor(() => expect(screen.queryByRole('alert')).not.toBeInTheDocument());
    });

    it('updates the current error, supports Escape, and does not make duplicate toasts', async () => {
        render(<><button>Mentés</button><GlobalErrorToast /></>);
        const origin = screen.getByRole('button', { name: 'Mentés' });
        origin.focus();
        emitError('Első hiba');
        const close = await screen.findByRole('button', { name: 'Hibaüzenet bezárása' });
        close.focus();
        emitError('Második hiba');
        expect(screen.getAllByRole('alert')).toHaveLength(1);
        expect(screen.getByRole('alert')).toHaveTextContent('Második hiba');
        expect(close).toHaveFocus();
        fireEvent.keyDown(close, { key: 'Escape' });
        expect(origin).toHaveFocus();
    });

    it('returns to main content if the originating control is gone', async () => {
        const view = render(<><main id="main-content" tabIndex={-1}>Tartalom</main><button>Mentés</button><GlobalErrorToast key="toast" /></>);
        screen.getByRole('button', { name: 'Mentés' }).focus();
        emitError('Hiba');
        await screen.findByRole('alert');
        view.rerender(<><main id="main-content" tabIndex={-1}>Tartalom</main><GlobalErrorToast key="toast" /></>);
        const close = screen.getByRole('button', { name: 'Hibaüzenet bezárása' });
        close.focus();
        fireEvent.click(close);
        expect(screen.getByRole('main')).toHaveFocus();
    });

    it('ignores empty or malformed error events', () => {
        render(<GlobalErrorToast />);
        emitError('');
        emitError({ message: 'invalid payload' });
        expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    });
});
