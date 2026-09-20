import { render, screen } from '@testing-library/react';
import ClickableText from '../ClickableText';
import { useAuth } from '../../context/AuthContext';
import { describe, it, expect, vi } from 'vitest';

// 1. Mockoljuk a Contextet, hogy ne kelljen valódi API hívásokat végezni
vi.mock('../../context/AuthContext', () => ({
    useAuth: vi.fn(),
}));

// 2. Mockoljuk a SmartWord komponenst, hogy lássuk, milyen propokat kap
vi.mock('../SmartWord', () => ({
    default: ({ word, preloadedTranslation }) => (
        <button data-testid="smart-word" data-hint={preloadedTranslation}>
            {word}
        </button>
    ),
}));

describe('ClickableText Component', () => {
    
    it('Csak a jelentést hordozó szavakat alakítja SmartWord-dé, a stop-words szavakat (the, is) kiszűri', () => {
        // Üres szótérképet adunk vissza (a diák még nem tud semmit)
        useAuth.mockReturnValue({ vocabularyMap: {} });
        
        render(<ClickableText text="The apple is red." source="LESSON" />);
        
        const smartWords = screen.getAllByTestId('smart-word');
        // A "The" és az "is" stop-word, így csak az "apple" és a "red" lesz kattintható!
        expect(smartWords).toHaveLength(2); 
        expect(smartWords[0]).toHaveTextContent('apple');
        expect(smartWords[1]).toHaveTextContent('red');
        
        expect(screen.getByText('.')).toBeInTheDocument(); // Az írásjel sima szöveg marad
    });

    it('A 3-as vagy magasabb SRS szintű szavakat sima szövegként rendereli', () => {
        // A diák az "apple" szót már mesteri szinten (3) tudja, a "red" pedig ismeretlen
        useAuth.mockReturnValue({ 
            vocabularyMap: { 'apple': 3 } 
        });
        
        const { container } = render(<ClickableText text="The apple is red." source="LESSON" />);
        
        const smartWords = screen.getAllByTestId('smart-word');
        // Mivel a "The" és "is" stop-word, az "apple" pedig SRS >= 3, csak a "red" marad kattintható
        expect(smartWords).toHaveLength(1); 
        expect(smartWords[0]).toHaveTextContent('red');
        
        // Az "apple" sima text node vagy <span> lett
        expect(container).toHaveTextContent('apple');
    });

    it('Feldolgozza és átadja a JSON hinteket a SmartWordnek, felülbírálva a stop-word szűrést is ha szükséges', () => {
        useAuth.mockReturnValue({ vocabularyMap: {} });
        
        render(
            <ClickableText 
                text="The apple is red." 
                hint="apple = alma, red = piros" 
                source="LESSON" 
            />
        );
        
        const smartWords = screen.getAllByTestId('smart-word');
        
        // Megkeressük az "apple" gombot, és ellenőrizzük az átadott data-hint értékét
        const appleBtn = smartWords.find(btn => btn.textContent === 'apple');
        expect(appleBtn).toHaveAttribute('data-hint', 'alma');
        
        const redBtn = smartWords.find(btn => btn.textContent === 'red');
        expect(redBtn).toHaveAttribute('data-hint', 'piros');
    });
});