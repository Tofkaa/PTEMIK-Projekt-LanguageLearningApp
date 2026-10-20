import { act, cleanup, fireEvent, render, screen } from '@testing-library/react';
import { MemoryRouter, Route, Routes, useNavigate } from 'react-router-dom';
import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest';
import ClassroomDetail from '../../pages/ClassroomDetail';
import { classroomApi } from '../../services/classroomApi';

const auth = vi.hoisted(() => ({ user: { userId: 'teacher', role: 'TEACHER' } }));
vi.mock('../../context/AuthContext', () => ({ useAuth: () => auth }));
vi.mock('../../services/classroomApi', () => ({ classroomApi: {
    getTeacherClassrooms: vi.fn(), getStudentClassrooms: vi.fn(),
} }));
vi.mock('../TeacherClassroomDetail', () => ({ default: ({ classroom }) => <h1>Tanári: {classroom.name}, {classroom.courseCode}</h1> }));
vi.mock('../StudentClassroomDetail', () => ({ default: ({ classroom }) => <h1>Diák: {classroom.name}</h1> }));

const room = { classroomId: 'room', name: 'Német csoport', courseCode: 'de' };
beforeEach(() => {
    vi.resetAllMocks();
    auth.user = { userId: 'teacher', role: 'TEACHER' };
    classroomApi.getTeacherClassrooms.mockResolvedValue({ data: [room] });
    classroomApi.getStudentClassrooms.mockResolvedValue({ data: [] });
});
afterEach(cleanup);

function Harness() {
    const navigate = useNavigate();
    return <><button onClick={() => navigate('/classrooms/other')}>Másik osztály</button><Routes>
        <Route path="/classrooms/:id" element={<ClassroomDetail />} />
    </Routes></>;
}
function open(state) {
    return render(<MemoryRouter initialEntries={[{ pathname: '/classrooms/room', state }]}><Harness /></MemoryRouter>);
}

describe('classroom view resolves from server data', () => {
    it('opens a teacher deep link without navigation state and restores server name and course', async () => {
        open();
        expect(await screen.findByRole('heading', { name: 'Tanári: Német csoport, de' })).toBeInTheDocument();
        expect(classroomApi.getStudentClassrooms).not.toHaveBeenCalled();
    });

    it('supports prefixed teacher roles and ignores stale name/course/ownership hints', async () => {
        auth.user.role = 'ROLE_TEACHER';
        open({ isOwner: false, className: 'Hamis név', courseCode: 'en' });
        expect(await screen.findByRole('heading', { name: 'Tanári: Német csoport, de' })).toBeInTheDocument();
    });

    it('does not trust a forged owner flag for students', async () => {
        auth.user = { userId: 'student', role: 'STUDENT' };
        classroomApi.getStudentClassrooms.mockResolvedValue({ data: [room] });
        open({ isOwner: true });
        expect(await screen.findByRole('heading', { name: 'Diák: Német csoport' })).toBeInTheDocument();
        expect(classroomApi.getTeacherClassrooms).not.toHaveBeenCalled();
    });

    it('uses student membership when a teacher does not own the room', async () => {
        classroomApi.getTeacherClassrooms.mockResolvedValue({ data: [] });
        classroomApi.getStudentClassrooms.mockResolvedValue({ data: [room] });
        open({ isOwner: true });
        expect(await screen.findByRole('heading', { name: 'Diák: Német csoport' })).toBeInTheDocument();
    });

    it('shows a recoverable error instead of guessing student access when the ownership request fails', async () => {
        classroomApi.getTeacherClassrooms.mockRejectedValueOnce(new Error('offline'));
        open();
        expect(await screen.findByRole('alert')).toHaveTextContent('nem tölthetők be');
        expect(classroomApi.getStudentClassrooms).not.toHaveBeenCalled();
        fireEvent.click(screen.getByRole('button', { name: 'Újrapróbálás' }));
        expect(await screen.findByRole('heading', { name: 'Tanári: Német csoport, de' })).toBeInTheDocument();
    });

    it('does not render either dashboard for a room absent from both lists', async () => {
        classroomApi.getTeacherClassrooms.mockResolvedValue({ data: [] });
        open({ isOwner: true });
        expect(await screen.findByRole('alert')).toHaveTextContent('nem található');
        expect(screen.getByRole('link', { name: 'Vissza az osztálytermekhez' })).toHaveAttribute('href', '/classrooms');
        expect(screen.queryByRole('heading', { name: /^Tanári:|^Diák:/ })).not.toBeInTheDocument();
    });

    it('ignores a delayed previous-room response after the route changes', async () => {
        let finish;
        classroomApi.getTeacherClassrooms.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }))
            .mockResolvedValueOnce({ data: [{ ...room, classroomId: 'other', name: 'Másik' }] });
        open();
        fireEvent.click(screen.getByRole('button', { name: 'Másik osztály' }));
        expect(await screen.findByRole('heading', { name: 'Tanári: Másik, de' })).toBeInTheDocument();
        await act(async () => finish({ data: [room] }));
        expect(screen.getByRole('heading', { name: 'Tanári: Másik, de' })).toBeInTheDocument();
    });

    it('clears the previous account view and ignores its delayed response on account change', async () => {
        let finish;
        classroomApi.getTeacherClassrooms.mockImplementationOnce(() => new Promise(resolve => { finish = resolve; }));
        const view = open();
        auth.user = { userId: 'student', role: 'STUDENT' };
        classroomApi.getStudentClassrooms.mockResolvedValue({ data: [room] });
        view.rerender(<MemoryRouter><Harness /></MemoryRouter>);
        expect(await screen.findByRole('heading', { name: 'Diák: Német csoport' })).toBeInTheDocument();
        await act(async () => finish({ data: [room] }));
        expect(screen.getByRole('heading', { name: 'Diák: Német csoport' })).toBeInTheDocument();
    });
});
