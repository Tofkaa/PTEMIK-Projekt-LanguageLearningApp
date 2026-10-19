import { useEffect, useState } from 'react';
import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react';
import { MemoryRouter, Route, Routes } from 'react-router-dom';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import ImageCropControls from '../ImageCropControls';
import LearningFeedback from '../LearningFeedback';
import Profile from '../../pages/Profile';
import Settings from '../../pages/Settings';
import api from '../../services/api';
import getCroppedImg from '../../utils/cropImage';

vi.mock('../../context/AuthContext', () => ({ useAuth: () => ({ user: {
    userId: 'test-user', name: 'Teszt', email: 'test@example.invalid', role: 'STUDENT', authProvider: 'LOCAL',
}, setUser: vi.fn(), logout: vi.fn() }) }));
vi.mock('../profile/AchievementSection', () => ({ default: () => null }));
vi.mock('../profile/RecentResultsSection', () => ({ default: () => null }));
vi.mock('../../services/api', () => ({ default: { post: vi.fn() } }));
vi.mock('../../utils/cropImage', () => ({ default: vi.fn() }));
vi.mock('react-easy-crop', () => ({ default: function MockCropper({ onCropComplete, onMediaLoaded, onCropSizeChange, crop }) {
    useEffect(() => {
        onMediaLoaded({ width: 400, height: 200 }); onCropSizeChange({ width: 200, height: 200 });
        onCropComplete({}, { x: 0, y: 0, width: 200, height: 200 });
    }, [onCropComplete, onMediaLoaded, onCropSizeChange]);
    return <div role="img" aria-label="Kivágási előnézet" data-position={`${crop.x},${crop.y}`} />;
} }));

beforeEach(() => {
    vi.clearAllMocks(); sessionStorage.clear();
    getCroppedImg.mockResolvedValue(new Blob(['test'], { type: 'image/jpeg' }));
});
afterEach(cleanup);

it('exposes alert and status roles only while feedback exists, preserving the live containers', () => {
    const { container, rerender } = render(<LearningFeedback label="Profilkép" />);
    const liveError = container.querySelector('[aria-live="assertive"]');
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.queryByRole('status')).not.toBeInTheDocument();
    rerender(<LearningFeedback label="Profilkép" error="Nem sikerült a feltöltés." />);
    expect(screen.getByRole('alert', { name: 'Profilkép: hiba' })).toHaveTextContent('Nem sikerült a feltöltés.');
    expect(container.querySelector('[aria-live="assertive"]')).toBe(liveError);
    rerender(<LearningFeedback label="Profilkép" feedback={{ type: 'success', msg: 'Sikerült.' }} />);
    expect(screen.queryByRole('alert')).not.toBeInTheDocument();
    expect(screen.getByRole('status', { name: 'Profilkép: állapot' })).toHaveTextContent('Sikerült.');
    expect(container.querySelector('[aria-live="assertive"]')).toBe(liveError);
});

function Controls({ disabled = false }) {
    const [crop, setCrop] = useState({ x: 0, y: 0 }); const [zoom, setZoom] = useState(1);
    return <><ImageCropControls crop={crop} onCropChange={setCrop} zoom={zoom} onZoomChange={setZoom}
        bounds={{ x: 15, y: 10 }} disabled={disabled} /><output aria-label="Pozíció">{crop.x},{crop.y}</output></>;
}

it('exposes the profile picture as a named native link', () => {
    render(<MemoryRouter><Profile /></MemoryRouter>);
    expect(screen.getByRole('heading', { level: 1, name: 'Felhasználói Profil' })).toBeInTheDocument();
    expect(screen.getByRole('link', { name: 'Profilkép módosítása' })).toHaveAttribute('href', '/settings');
});

it('opens the account settings from the avatar even when another tab was last visited', async () => {
    sessionStorage.setItem('settingsActiveTab', 'learning');
    render(<MemoryRouter initialEntries={['/profile']}><Routes>
        <Route path="/profile" element={<Profile />} /><Route path="/settings" element={<Settings />} />
    </Routes></MemoryRouter>);
    fireEvent.click(screen.getByRole('link', { name: 'Profilkép módosítása' }));
    expect(await screen.findByRole('tab', { name: 'Fiókadatok' })).toHaveAttribute('aria-selected', 'true');
    expect(screen.getByRole('button', { name: 'Profilkép kiválasztása' })).toBeInTheDocument();
    expect(screen.queryByLabelText('Profilkép: hiba')).not.toBeInTheDocument();
    expect(screen.queryByLabelText('Felhasználónév: hiba')).not.toBeInTheDocument();
});

it('moves and centers the image without dragging, preserving crop limits and zoom', () => {
    render(<Controls />);
    const zoom = screen.getByRole('slider', { name: 'Nagyítás (Zoom)' });
    fireEvent.change(zoom, { target: { value: '2' } });
    expect(zoom).toHaveAttribute('aria-valuetext', '200 százalék');
    fireEvent.click(screen.getByRole('button', { name: 'Kép mozgatása jobbra' }));
    fireEvent.click(screen.getByRole('button', { name: 'Kép mozgatása jobbra' }));
    fireEvent.click(screen.getByRole('button', { name: 'Kép mozgatása lefelé' }));
    expect(screen.getByLabelText('Pozíció')).toHaveTextContent('15,10');
    fireEvent.click(screen.getByRole('button', { name: 'Kép középre' }));
    expect(screen.getByLabelText('Pozíció')).toHaveTextContent('0,0'); expect(zoom).toHaveValue('2');
});

it('disables all alternative controls while an upload is running', () => {
    render(<Controls disabled />);
    screen.getAllByRole('button').forEach(button => expect(button).toBeDisabled());
    expect(screen.getByRole('slider')).toBeDisabled();
});

async function selectImage() {
    render(<MemoryRouter><Settings /></MemoryRouter>);
    const input = screen.getByLabelText('Új profilkép fájlja');
    fireEvent.change(input, { target: { files: [new File(['test'], 'test.png', { type: 'image/png' })] } });
    return screen.findByRole('dialog', { name: 'Profilkép beállítása' });
}

it('keeps the selected image and crop after a failed upload, allowing a retry', async () => {
    api.post.mockRejectedValueOnce(new Error('offline')).mockResolvedValueOnce({ data: '/test-profile.jpg' });
    const dialog = await selectImage();
    fireEvent.click(within(dialog).getByRole('button', { name: 'Kép mozgatása jobbra' }));
    const preview = within(dialog).getByRole('img', { name: 'Kivágási előnézet' });
    expect(preview).toHaveAttribute('data-position', '10,0');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Kivágás és Mentés' }));
    await waitFor(() => expect(within(dialog).getByRole('alert')).toHaveTextContent('Nem sikerült feltölteni'));
    expect(preview).toHaveAttribute('data-position', '10,0');
    fireEvent.click(within(dialog).getByRole('button', { name: 'Kivágás és Mentés' }));
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
    expect(api.post).toHaveBeenCalledTimes(2);
    expect(screen.getByRole('status', { name: 'Profilkép: állapot' })).toHaveTextContent('Profilkép sikeresen frissítve');
});

it('announces unsupported files without opening the crop dialog', () => {
    render(<MemoryRouter><Settings /></MemoryRouter>);
    fireEvent.change(screen.getByLabelText('Új profilkép fájlja'), { target: { files: [new File(['text'], 'text.txt', { type: 'text/plain' })] } });
    expect(screen.getByRole('alert', { name: 'Profilkép: hiba' })).toHaveTextContent('Nem támogatott fájlformátum');
    expect(screen.queryByRole('dialog')).not.toBeInTheDocument(); expect(api.post).not.toHaveBeenCalled();
});

it('keeps focus on the save button and prevents duplicate uploads while saving', async () => {
    let finishUpload;
    api.post.mockImplementation(() => new Promise(resolve => { finishUpload = resolve; }));
    const dialog = await selectImage();
    const save = within(dialog).getByRole('button', { name: 'Kivágás és Mentés' });
    save.focus(); fireEvent.click(save);
    await waitFor(() => expect(api.post).toHaveBeenCalledTimes(1));
    expect(save).toHaveFocus(); expect(save).toHaveAttribute('aria-busy', 'true');
    fireEvent.click(save); expect(api.post).toHaveBeenCalledTimes(1);
    finishUpload({ data: '/test-profile.jpg' });
    await waitFor(() => expect(screen.queryByRole('dialog')).not.toBeInTheDocument());
});
