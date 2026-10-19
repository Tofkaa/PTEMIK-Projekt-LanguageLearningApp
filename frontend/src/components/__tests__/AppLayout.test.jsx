import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react';
import { useState } from 'react';
import { Modal } from 'react-bootstrap';
import { MemoryRouter, useNavigate } from 'react-router-dom';
import { afterEach, describe, expect, it } from 'vitest';
import AppLayout from '../AppLayout';

function ExamplePage() {
    const navigate = useNavigate();
    return <AppLayout navigation={<nav aria-label="Fő navigáció">Menü</nav>}>
        <h1>Tartalom</h1>
        <input aria-label="Válasz" />
        <button onClick={() => navigate('/vocabulary/practice')}>Gyakorlás</button>
        <button onClick={() => navigate('?page=2')}>Lapozás</button>
    </AppLayout>;
}

afterEach(cleanup);

describe('accessible application layout', () => {
    it('excludes the background from screen-reader browsing during a real modal and restores it after closing', async () => {
        function ModalPage() {
            const [open, setOpen] = useState(false);
            return <AppLayout navigation={<nav>Háttérmenü</nav>}>
                <button onClick={() => setOpen(true)}>Megnyitás</button>
                <Modal show={open} onHide={() => setOpen(false)} aria-labelledby="example-modal-title">
                    <Modal.Header><Modal.Title id="example-modal-title">Próbaablak</Modal.Title></Modal.Header>
                    <Modal.Body><button onClick={() => setOpen(false)}>Bezárás</button></Modal.Body>
                </Modal>
            </AppLayout>;
        }
        render(<MemoryRouter><ModalPage /></MemoryRouter>);
        const trigger = screen.getByRole('button', { name: 'Megnyitás' });
        trigger.focus();
        fireEvent.click(trigger);
        const dialog = await screen.findByRole('dialog', { name: 'Próbaablak' });
        await waitFor(() => expect(screen.queryByRole('navigation')).not.toBeInTheDocument());
        expect(screen.queryByRole('link', { name: 'Ugrás a tartalomra' })).not.toBeInTheDocument();
        expect(dialog.closest('[aria-hidden="true"]')).toBeNull();
        fireEvent.click(screen.getByRole('button', { name: 'Bezárás' }));
        await waitFor(() => expect(screen.getByRole('navigation')).toBeInTheDocument());
        await waitFor(() => expect(trigger).toHaveFocus());
    });
    it.each([
        ['/login', 'Bejelentkezés'],
        ['/assignment/session/123/play', 'Feladatmegoldás'],
        ['/assignment/123/submissions', 'Beadások értékelése'],
        ['/missing', 'Az oldal nem található'],
    ])('sets a meaningful title on direct entry to %s', (path, title) => {
        render(<MemoryRouter initialEntries={[path]}><ExamplePage /></MemoryRouter>);
        expect(document.title).toBe(`${title} | LanguageApp`);
    });

    it('moves focus to the main content and updates the title after navigation', () => {
        render(<MemoryRouter initialEntries={['/login']}><ExamplePage /></MemoryRouter>);
        fireEvent.click(screen.getByRole('button', { name: 'Gyakorlás' }));
        expect(document.title).toBe('Szókincsgyakorlás | LanguageApp');
        expect(screen.getByRole('main', { name: 'Fő tartalom' })).toHaveFocus();
    });

    it('keeps focus on in-page updates and query changes', () => {
        render(<MemoryRouter initialEntries={['/login']}><ExamplePage /></MemoryRouter>);
        const answer = screen.getByRole('textbox');
        answer.focus();
        fireEvent.change(answer, { target: { value: 'alma' } });
        fireEvent.click(screen.getByRole('button', { name: 'Lapozás' }));
        expect(answer).toHaveFocus();
    });

    it('provides a skip link before navigation that focuses main content', () => {
        render(<MemoryRouter><ExamplePage /></MemoryRouter>);
        const skip = screen.getByRole('link', { name: 'Ugrás a tartalomra' });
        expect(skip).toHaveAttribute('href', '#main-content');
        expect(skip.compareDocumentPosition(screen.getByRole('navigation')) & Node.DOCUMENT_POSITION_FOLLOWING).toBeTruthy();
        fireEvent.click(skip);
        expect(screen.getByRole('main')).toHaveFocus();
    });
});
