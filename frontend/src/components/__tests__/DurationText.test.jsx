import { cleanup, render, screen } from '@testing-library/react';
import { afterEach, expect, it } from 'vitest';
import DurationText from '../DurationText';

afterEach(cleanup);

it.each([[0, '00:00', '0 perc 0 másodperc'], [114, '01:54', '1 perc 54 másodperc'],
    [3601, '60:01', '60 perc 1 másodperc']])('exposes explicit units for %s seconds while retaining the visual clock', (seconds, clock, speech) => {
    render(<span role="timer" aria-live="off" aria-labelledby="duration"><DurationText id="duration" seconds={seconds} label="Eltelt idő" /></span>);
    expect(screen.getByText(clock)).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByRole('timer', { name: `Eltelt idő: ${speech}` })).toHaveAttribute('aria-live', 'off');
    expect(screen.getByText(`Eltelt idő: ${speech}`)).toHaveAttribute('lang', 'hu');
});

it('names overtime explicitly without making the plus-prefixed clock the spoken duration', () => {
    render(<span role="timer" aria-labelledby="duration"><DurationText id="duration" seconds={-114} overtime label="Időtúllépés" /></span>);
    expect(screen.getByText('+01:54')).toHaveAttribute('aria-hidden', 'true');
    expect(screen.getByRole('timer', { name: 'Időtúllépés: 1 perc 54 másodperc' })).toBeInTheDocument();
});
