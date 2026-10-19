import { useId, useLayoutEffect, useRef } from 'react';

/** Preserve duplicate words and restore focus when an activated token moves. */
const WordBankExercise = ({ data, currentAnswer, onAnswer, disabled = false }) => {
    const id = useId();
    const buttons = useRef(new Map());
    const pendingFocus = useRef(null);
    const selectedWords = currentAnswer?.trim() ? currentAnswer.trim().split(/\s+/) : [];
    const tokens = (data?.options || []).map((word, id) => ({ word, id }));
    const used = new Set();
    const selectedIds = selectedWords.map(word => {
        const token = tokens.find(token => token.word === word && !used.has(token.id));
        if (token) used.add(token.id);
        return token?.id;
    });
    const available = tokens.filter(token => !used.has(token.id));

    useLayoutEffect(() => {
        if (pendingFocus.current !== null) {
            buttons.current.get(pendingFocus.current)?.focus();
            pendingFocus.current = null;
        }
    }, [currentAnswer]);

    const registerButton = key => node => {
        if (node) buttons.current.set(key, node);
        else buttons.current.delete(key);
    };

    const select = token => {
        if (disabled) return;
        const removed = available.find(item => item.word === token.word);
        const remaining = available.filter(item => item.id !== removed.id);
        const next = remaining.find(item => item.id > token.id) || remaining.at(-1);
        pendingFocus.current = next ? `available-${next.id}` : `selected-${selectedWords.length}`;
        onAnswer([...selectedWords, token.word].join(' '));
    };

    const deselect = index => {
        if (disabled) return;
        const remaining = selectedWords.filter((_, position) => position !== index);
        pendingFocus.current = remaining.length
            ? `selected-${Math.min(index, remaining.length - 1)}`
            : `available-${selectedIds[index]}`;
        onAnswer(remaining.join(' '));
    };

    return <fieldset disabled={disabled} className="word-bank text-center">
        <legend className="visually-hidden">Mondatépítés</legend>
        <p lang="hu" className="small text-body-secondary">Válassz szavakat a mondathoz. Enter vagy Szóköz hozzáadja a kiválasztott szót. Az összeállított válasz szavait ugyanígy eltávolíthatod. A teljes mondat külön Tab-lépéssel is elérhető.</p>
        <div role="group" aria-label="Összeállított válasz"
            className="word-bank-answer mb-4 p-3 d-flex flex-wrap gap-2 justify-content-center align-items-center">
            {selectedWords.length === 0 && <span className="text-body-secondary fst-italic">Még nem választottál szót.</span>}
            {selectedWords.map((word, index) => <button key={`selected-${index}`} type="button"
                lang={data?.answerLang}
                aria-labelledby={`${id}-selected-word-${index}`}
                className="word-token word-token-selected" disabled={disabled}
                ref={registerButton(`selected-${index}`)} onClick={() => deselect(index)}>
                <span id={`${id}-selected-word-${index}`} lang={data?.answerLang}>{word}</span>
            </button>)}
        </div>
        {currentAnswer && <p tabIndex={0} lang={data?.answerLang} className="word-bank-sentence text-body mb-4">{currentAnswer}</p>}
        <div role="group" aria-label="Választható szavak" className="d-flex flex-wrap gap-2 justify-content-center">
            {available.map(token => <button key={token.id} type="button" className="word-token"
                lang={data?.answerLang}
                aria-labelledby={`${id}-available-word-${token.id}`}
                disabled={disabled} ref={registerButton(`available-${token.id}`)} onClick={() => select(token)}>
                <span id={`${id}-available-word-${token.id}`} lang={data?.answerLang}>{token.word}</span>
            </button>)}
        </div>
        <p role="status" aria-atomic="true" lang="hu" className="visually-hidden">
            {selectedWords.length ? `${selectedWords.length} szó az összeállított válaszban.` : 'Az összeállított válasz üres.'}
        </p>
    </fieldset>;
};

export default WordBankExercise;
