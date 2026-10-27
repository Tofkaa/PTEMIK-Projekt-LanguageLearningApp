import { act, cleanup, render, screen, waitFor } from '@testing-library/react';
import { afterEach, beforeEach, expect, it, vi } from 'vitest';
import useScopedData from './useScopedData';
import api from '../services/api';

vi.mock('../services/api', () => ({ default: { get: vi.fn() } }));
let requests;
beforeEach(() => {
    requests = [];
    api.get.mockImplementation((url, config) => new Promise((resolve, reject) => requests.push({ url, ...config, resolve, reject })));
});
afterEach(cleanup);
function Probe({ scope, enabled = true }) {
    const { data, isLoading, error } = useScopedData('/lessons', scope, enabled);
    return <output>{isLoading ? 'loading' : error ? 'error' : data.join(',')}</output>;
}

it.each(['another-course', 'another-account'])('discards an older response after switching to %s', async scope => {
    const view = render(<Probe scope="first" />);
    await waitFor(() => expect(requests).toHaveLength(1));
    view.rerender(<Probe scope={scope} />);
    expect(requests[0].signal.aborted).toBe(true);
    await act(() => requests[1].resolve({ data: ['new'] }));
    await act(() => requests[0].resolve({ data: ['old'] }));
    expect(screen.getByRole('status')).toHaveTextContent('new');
});

it('hides cached data immediately and ignores an old failure while the replacement is loading', async () => {
    const view = render(<Probe scope="first" />);
    await act(() => requests[0].resolve({ data: ['first'] }));
    view.rerender(<Probe scope="second" />);
    expect(screen.getByRole('status')).toHaveTextContent('loading');
    view.rerender(<Probe scope="third" />);
    await act(() => requests[1].reject(new Error('late old failure')));
    expect(screen.getByRole('status')).toHaveTextContent('loading');
    await act(() => requests[2].resolve({ data: ['third'] }));
    expect(screen.getByRole('status')).toHaveTextContent('third');
});

it('aborts pending work on logout/disable and unmount', async () => {
    const view = render(<Probe scope="first" />);
    view.rerender(<Probe scope="first" enabled={false} />);
    expect(requests[0].signal.aborted).toBe(true);
    await act(() => requests[0].resolve({ data: ['private data'] }));
    expect(screen.getByRole('status')).toBeEmptyDOMElement();
    view.rerender(<Probe scope="next" />);
    view.unmount();
    expect(requests[1].signal.aborted).toBe(true);
});

it('reports a current failure instead of pretending an empty vocabulary loaded successfully', async () => {
    render(<Probe scope="first" />);
    await act(() => requests[0].reject(new Error('offline')));
    expect(screen.getByRole('status')).toHaveTextContent('error');
});
