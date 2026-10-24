import { act, cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import AssignmentPlayer from '../../pages/AssignmentPlayer';
import { assignmentApi } from '../../services/assignmentApi';
import api from '../../services/api';

vi.mock('../../services/assignmentApi', () => ({ assignmentApi: { submitAssignment: vi.fn(), getSessionTiming: vi.fn() } }));
vi.mock('../../services/api', () => ({ default: { post: vi.fn() } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { activeCourseCode: 'en' }, vocabularyMap: {} }) }));
const NOW = Date.parse('2026-10-21T12:00:00Z');
const exercises = [
    { exerciseId: 'choice', type: 'MULTIPLE_CHOICE', content: {
        question: 'What color is a banana?', questionLang: 'en', answerLang: 'en', options: ['Red', 'Yellow'],
    } },
    { exerciseId: 'translation', type: 'TRANSLATION', content: { question: 'Alma', questionLang: 'hu', answerLang: 'en' } },
];
beforeEach(() => {
    vi.resetAllMocks();
    assignmentApi.submitAssignment.mockResolvedValue({});
});
afterEach(() => { cleanup(); vi.useRealTimers(); vi.restoreAllMocks(); });

function openPlayer(timed = false, rules = {}) {
    const timing = timed ? { deadline: new Date(NOW + 2000).toISOString(), serverNow: new Date(NOW).toISOString(),
        allowLateSubmission: false, finished: false } : null;
    assignmentApi.getSessionTiming.mockImplementation(async () => ({ data: { ...timing, serverNow: new Date(Date.now()).toISOString() } }));
    render(<MemoryRouter initialEntries={[{ pathname: '/assignment/session/session/play', state: {
        sessionData: { exercises, timing }, assignmentDetails: { hasFeedback: false, allowRetries: false, ...rules },
    } }]}><Routes><Route path="/assignment/session/:sessionId/play" element={<AssignmentPlayer />} /></Routes></MemoryRouter>);
}
function enterReview() {
    fireEvent.click(screen.getByRole('radio', { name: 'Red' }));
    fireEvent.click(screen.getByRole('button', { name: 'Tovább' }));
    fireEvent.change(screen.getByRole('textbox', { name: 'Válaszod' }), { target: { value: 'apple' } });
    fireEvent.click(screen.getByRole('button', { name: 'Válaszok áttekintése' }));
}
function editFirstAnswer() {
    const origin = screen.getByRole('button', { name: 'Válasz javítása: 1. feladat' });
    origin.focus();
    fireEvent.click(origin);
    fireEvent.click(within(screen.getByRole('dialog')).getByRole('radio', { name: 'Yellow' }));
}

describe('review before final assignment submission', () => {
    it('requires confirmation, lets earlier answers change, and submits only the latest draft once', async () => {
        let finish;
        assignmentApi.submitAssignment.mockReturnValue(new Promise(resolve => { finish = resolve; }));
        openPlayer(); enterReview();
        expect(screen.getByRole('heading', { name: 'Válaszok áttekintése' })).toHaveFocus();
        expect(assignmentApi.submitAssignment).not.toHaveBeenCalled();
        expect(screen.getByText('apple')).toHaveAttribute('lang', 'en');
        const origin = screen.getByRole('button', { name: 'Válasz javítása: 1. feladat' });
        origin.scrollIntoView = vi.fn();
        editFirstAnswer();
        fireEvent.click(screen.getByRole('button', { name: 'Kész, vissza az áttekintéshez' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(screen.getByRole('button', { name: 'Válasz javítása: 1. feladat' })).toHaveFocus();
        await waitFor(() => expect(origin.scrollIntoView).toHaveBeenCalledWith({ block: 'center', inline: 'nearest' }));
        const confirm = screen.getByRole('button', { name: 'Végleges beadás' });
        fireEvent.click(confirm); fireEvent.click(confirm);
        expect(assignmentApi.submitAssignment).toHaveBeenCalledExactlyOnceWith('session', [
            { exerciseId: 'choice', answer: 'Yellow', isRetry: undefined, retried: false },
            { exerciseId: 'translation', answer: 'apple', isRetry: undefined, retried: false },
        ]);
        expect(api.post).not.toHaveBeenCalled();
        await act(async () => finish({}));
        expect(screen.getByRole('heading', { name: 'Sikeres Beküldés!' })).toHaveFocus();
    });

    it('prevents accidental empty submission and retains corrections after a failed final request', async () => {
        vi.spyOn(console, 'error').mockImplementation(() => {});
        assignmentApi.submitAssignment.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({});
        openPlayer(); enterReview();
        fireEvent.click(screen.getByRole('button', { name: 'Válasz javítása: 2. feladat' }));
        const input = within(screen.getByRole('dialog')).getByRole('textbox', { name: 'Válaszod' });
        fireEvent.change(input, { target: { value: '' } });
        fireEvent.click(screen.getByRole('button', { name: 'Kész, vissza az áttekintéshez' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        expect(screen.getByRole('button', { name: 'Végleges beadás' })).toHaveAttribute('aria-disabled', 'true');
        fireEvent.click(screen.getByRole('button', { name: 'Végleges beadás' }));
        expect(assignmentApi.submitAssignment).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('button', { name: 'Válasz javítása: 2. feladat' }));
        fireEvent.change(within(screen.getByRole('dialog')).getByRole('textbox', { name: 'Válaszod' }), { target: { value: 'an apple' } });
        fireEvent.click(screen.getByRole('button', { name: 'Kész, vissza az áttekintéshez' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        fireEvent.click(screen.getByRole('button', { name: 'Végleges beadás' }));
        await screen.findByRole('alert');
        expect(screen.getByText('an apple')).toBeInTheDocument();
        fireEvent.click(screen.getByRole('button', { name: 'Beadás újrapróbálása' }));
        await screen.findByRole('heading', { name: 'Sikeres Beküldés!' });
        expect(assignmentApi.submitAssignment.mock.calls[1][1][1].answer).toBe('an apple');
    });

    it('automatically submits the latest edit even while its editor is open at expiry', async () => {
        vi.useFakeTimers(); vi.setSystemTime(NOW);
        openPlayer(true); enterReview(); editFirstAnswer();
        await act(async () => vi.advanceTimersByTimeAsync(3000));
        expect(assignmentApi.submitAssignment).toHaveBeenCalledTimes(1);
        expect(assignmentApi.submitAssignment.mock.calls[0][1][0].answer).toBe('Yellow');
        expect(screen.queryByRole('dialog')).not.toBeInTheDocument();
        expect(screen.getByRole('heading', { name: 'Sikeres Beküldés!' })).toHaveFocus();
    });

    it('applies a live teacher extension during review without submitting or losing draft edits', async () => {
        vi.useFakeTimers(); vi.setSystemTime(NOW);
        openPlayer(true); enterReview(); editFirstAnswer();
        assignmentApi.getSessionTiming.mockImplementation(async () => ({ data: {
            deadline: new Date(NOW + 600000).toISOString(), serverNow: new Date(Date.now()).toISOString(),
            allowLateSubmission: false, finished: false,
        } }));
        await act(async () => vi.advanceTimersByTimeAsync(3000));
        expect(assignmentApi.submitAssignment).not.toHaveBeenCalled();
        expect(within(screen.getByRole('dialog')).getByRole('radio', { name: 'Yellow' })).toBeChecked();
        expect(within(screen.getByRole('dialog')).getByRole('status')).toHaveTextContent('A tanár frissítette az időbeállításaidat.');
    });

    it('does not duplicate a manual submission if the timer expires while it is in flight', async () => {
        vi.useFakeTimers(); vi.setSystemTime(NOW);
        let finish;
        assignmentApi.submitAssignment.mockReturnValue(new Promise(resolve => { finish = resolve; }));
        openPlayer(true); enterReview();
        fireEvent.click(screen.getByRole('button', { name: 'Végleges beadás' }));
        await act(async () => vi.advanceTimersByTimeAsync(3000));
        expect(assignmentApi.submitAssignment).toHaveBeenCalledTimes(1);
        await act(async () => finish({}));
    });

    it('preserves genuine retry history when a repeated exercise is corrected in the final review', async () => {
        api.post.mockResolvedValueOnce({ data: { correct: false } })
            .mockResolvedValue({ data: { correct: true } });
        openPlayer(false, { allowRetries: true });
        fireEvent.click(screen.getByRole('radio', { name: 'Red' }));
        fireEvent.click(screen.getByRole('button', { name: 'Tovább' }));
        await screen.findByRole('textbox', { name: 'Válaszod' });
        fireEvent.change(screen.getByRole('textbox', { name: 'Válaszod' }), { target: { value: 'apple' } });
        fireEvent.click(screen.getByRole('button', { name: 'Tovább' }));
        await screen.findByRole('radio', { name: 'Yellow' });
        fireEvent.click(screen.getByRole('radio', { name: 'Yellow' }));
        fireEvent.click(screen.getByRole('button', { name: 'Válaszok áttekintése' }));
        await screen.findByRole('heading', { name: 'Válaszok áttekintése' });
        // The cloned retry appears only once in the review, with its history intact.
        expect(screen.getAllByRole('button', { name: /Válasz javítása:/ })).toHaveLength(2);
        editFirstAnswer();
        fireEvent.click(within(screen.getByRole('dialog')).getByRole('radio', { name: 'Red' }));
        fireEvent.click(screen.getByRole('button', { name: 'Kész, vissza az áttekintéshez' }));
        await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
        fireEvent.click(screen.getByRole('button', { name: 'Végleges beadás' }));
        expect(assignmentApi.submitAssignment.mock.calls[0][1][0]).toEqual({
            exerciseId: 'choice', answer: 'Red', isRetry: undefined, retried: true,
        });
        await screen.findByRole('heading', { name: 'Sikeres Beküldés!' });
        expect(api.post).toHaveBeenCalledTimes(3);
    });
});
