import { act, cleanup, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import StudentClassroomDetail from '../StudentClassroomDetail';
import { assignmentApi } from '../../services/assignmentApi';

vi.mock('../../services/assignmentApi', () => ({
    assignmentApi: {
        getClassroomAssignments: vi.fn(),
        getStudentClassroomStatistics: vi.fn(),
    },
}));
vi.mock('../../services/classroomApi', () => ({
    classroomApi: { getMembers: vi.fn(async () => ({ data: [] })) },
}));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: { userId: 'student' } }) }));
vi.mock('../../context/NotificationContext', () => ({ useNotifications: () => ({ notifications: {} }) }));
vi.mock('../../utils/viewedStore', () => ({ getUnseenIds: () => [], markAsViewed: vi.fn() }));
vi.mock('../ExercisePreviewCard', () => ({ default: () => null }));

const start = new Date('2026-10-16T16:18:00Z').getTime();

async function openClassroom() {
    await act(async () => {
        render(<MemoryRouter initialEntries={['/classrooms/test']}>
            <Routes><Route path="/classrooms/:id" element={<StudentClassroomDetail />} /></Routes>
        </MemoryRouter>);
    });
}

describe('scheduled classroom assignment', () => {
    beforeEach(() => {
        vi.useFakeTimers();
        vi.setSystemTime(start - 1000);
        assignmentApi.getClassroomAssignments.mockResolvedValue({ data: [{
            assignmentId: 'assignment', title: 'TimeZoneTest',
            availableFrom: new Date(start).toISOString(),
            availableUntil: new Date(start + 60_000).toISOString(),
            attemptsUsed: 0, test: true, completed: false,
        }] });
        assignmentApi.getStudentClassroomStatistics.mockResolvedValue({ data: null });
    });

    afterEach(() => {
        cleanup();
        vi.useRealTimers();
        vi.clearAllMocks();
    });

    it('enables starting at the scheduled instant without reloading or refetching', async () => {
        await openClassroom();
        expect(screen.getByRole('button', { name: 'Még nem elérhető: TimeZoneTest' })).toBeDisabled();
        const requests = assignmentApi.getClassroomAssignments.mock.calls.length;

        act(() => vi.advanceTimersByTime(1000));

        expect(screen.getByRole('button', { name: 'Indítás: TimeZoneTest' })).toBeEnabled();
        expect(assignmentApi.getClassroomAssignments).toHaveBeenCalledTimes(requests);

        act(() => vi.advanceTimersByTime(60_000));
        expect(screen.queryByRole('button', { name: 'Indítás: TimeZoneTest' })).not.toBeInTheDocument();
        expect(screen.getByText('Nincs aktív feladatod. 🎉')).toBeInTheDocument();
    });

    it.each(['focus', 'visibilitychange'])('updates immediately on %s after the tab was inactive', async (eventName) => {
        await openClassroom();
        expect(screen.getByRole('button', { name: 'Még nem elérhető: TimeZoneTest' })).toBeDisabled();

        act(() => {
            vi.setSystemTime(start);
            (eventName === 'focus' ? window : document).dispatchEvent(new Event(eventName));
        });

        expect(screen.getByRole('button', { name: 'Indítás: TimeZoneTest' })).toBeEnabled();
    });

    it.each([false, true])('keeps an ongoing late-enabled attempt visible after the deadline (previous completion=%s)', async completed => {
        vi.setSystemTime(start + 120000);
        assignmentApi.getClassroomAssignments.mockResolvedValue({data:[{
            assignmentId:'assignment',title:'Late test',completed,attemptsUsed: completed ? 1 : 0,
            availableUntil:new Date(start+60000).toISOString(),allowLateSubmission:true,ongoingSessionId:'ongoing',
        }]});
        await openClassroom();
        expect(screen.getByRole('button',{name:'Folytatás: Late test'})).toBeEnabled();
    });

    it('does not offer a new late attempt when there is no ongoing session', async () => {
        vi.setSystemTime(start+120000);
        assignmentApi.getClassroomAssignments.mockResolvedValue({data:[{
            assignmentId:'assignment',title:'Expired test',completed:false,attemptsUsed:0,
            availableUntil:new Date(start+60000).toISOString(),allowLateSubmission:true,
        }]});
        await openClassroom();
        expect(screen.queryByRole('button',{name:'Indítás: Expired test'})).not.toBeInTheDocument();
    });
});
