import { useEffect, useId, useRef, useState } from 'react';
import { OverlayTrigger, Popover, Spinner, Badge } from 'react-bootstrap';
import { vocabularyApi } from '../services/vocabularyApi';
import { formatToLocalDisplay } from '../utils/dateUtils';
import { useAuth } from '../context/AuthContext';
import { useVocabularyCourse } from '../context/VocabularyCourseContext';
import { getAuthEpoch } from '../services/authSession';

/** Opens a translation and preserves the existing lookup/SRS side effects. */
const SmartWord = props => {
    const { user } = useAuth() || {};
    const course = useVocabularyCourse();
    const scope = JSON.stringify([getAuthEpoch(), user?.userId, course.courseId, course.courseCode, course.revision, props.word,
        props.queryWord, props.lang, props.source, props.preloadedTranslation, props.disabled]);
    return <WordTranslation key={scope} {...props} courseId={course.courseId} />;
};

const WordTranslation = ({ word, queryWord, disabled = false, source = 'LESSON', preloadedTranslation = null, onWordClick, lang, courseId }) => {
    const id = useId();
    const triggerRef = useRef(null);
    const closeRef = useRef(null);
    const [show, setShow] = useState(false);
    const [translationData, setTranslationData] = useState(preloadedTranslation
        ? { translation: preloadedTranslation, srsLevel: 'Beépített tipp', newAddition: false, isHint: true }
        : null);
    const [isLoading, setIsLoading] = useState(false);
    const [error, setError] = useState(null);
    const alive = useRef(false);
    const lookup = useRef(null);
    useEffect(() => {
        alive.current = true;
        return () => { alive.current = false; lookup.current?.abort(); };
    }, []);

    const handleToggle = async nextShow => {
        setShow(nextShow);
        if (!nextShow) return;
        onWordClick?.();
        if (translationData || isLoading) return;
        setIsLoading(true);
        setError(null);
        const controller = new AbortController();
        lookup.current = controller;
        try {
            const response = await vocabularyApi.lookupWord(queryWord || word.toLowerCase(), source, { signal: controller.signal, courseId });
            if (!alive.current || controller.signal.aborted) return;
            setTranslationData(response.data);
        } catch {
            if (!alive.current || controller.signal.aborted) return;
            setError('Fordítás nem elérhető. Zárd be, majd próbáld újra.');
        } finally {
            if (alive.current) setIsLoading(false);
        }
    };

    const close = () => {
        setShow(false);
        triggerRef.current?.focus();
    };

    const handleKeyDown = event => {
        if (event.key === 'Escape') {
            event.preventDefault();
            event.stopPropagation();
            close();
        } else if (event.key === 'Tab') {
            // Continue the sentence's tab order instead of jumping to the portal's position.
            close();
            if (event.shiftKey) event.preventDefault();
        }
    };

    if (disabled) return <span lang={lang}>{word}</span>;

    const popover = <Popover id={id} role="dialog" lang="hu" aria-labelledby={`${id}-title`}
        aria-describedby={`${id}-body`} className="word-translation shadow-lg" onKeyDown={handleKeyDown}>
        <Popover.Header className="d-flex justify-content-between align-items-center gap-3">
            <strong id={`${id}-title`}>Fordítás: <span lang={lang}>{word}</span></strong>
            <button ref={closeRef} type="button" className="translation-close" onClick={close}>Bezárás</button>
        </Popover.Header>
        <Popover.Body id={`${id}-body`}>
            <div role="status" aria-atomic="true">
                {isLoading ? <div className="d-flex align-items-center py-2">
                    <Spinner animation="border" size="sm" className="me-2" aria-hidden="true" />
                    <span>Keresés...</span>
                </div> : error ? <span>{error}</span> : translationData ? <>
                    <div className="fw-bold fs-6 mb-2">{translationData.translation}</div>
                    {translationData.newAddition && <Badge bg="success" className="mb-2">Új szótári bejegyzés</Badge>}
                    <div className="pt-2 mt-2 border-top small text-body-secondary">
                        <div>Szint: {translationData.srsLevel}{translationData.isHint ? '' : '/6'}</div>
                        {!translationData.isHint && translationData.nextPracticeAt && <div>
                            Következő ismétlés: {formatToLocalDisplay(translationData.nextPracticeAt, false)}
                        </div>}
                    </div>
                </> : null}
            </div>
        </Popover.Body>
    </Popover>;

    return <OverlayTrigger trigger="click" placement="top" flip overlay={popover} show={show}
        onToggle={handleToggle} rootClose onEntered={() => closeRef.current?.focus({ preventScroll: true })}>
        <button ref={triggerRef} type="button" className="smart-word" aria-haspopup="dialog"
            lang={lang}
            aria-labelledby={`${id}-word`}
            aria-expanded={show} aria-controls={show ? id : undefined}
            onKeyDown={event => { if (event.key === 'Escape') close(); }}>
            <span id={`${id}-word`} lang={lang}>{word}</span>
        </button>
    </OverlayTrigger>;
};

export default SmartWord;
