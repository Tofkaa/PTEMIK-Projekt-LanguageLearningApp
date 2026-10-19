import React from 'react';
import { Badge } from 'react-bootstrap';
import ExerciseImage from './ExerciseImage';
import LanguageText from './LanguageText';
import TranslationHint from './TranslationHint';

const ExercisePreviewCard = ({ exercise, serverCorrectAnswer }) => {
    if (!exercise) return <div className="text-secondary small">Nincs elérhető feladat adat.</div>;

    const content = exercise.content || {};

    return (
        <div className="p-3 bg-black bg-opacity-50 rounded border border-info small shadow-inner text-light mt-2">
            
            <div className="mb-2 border-bottom border-secondary pb-2">
                <Badge bg="secondary" className="mb-3">{exercise.type}</Badge>
                
                <ExerciseImage exercise={exercise} maxHeight={180} />
                
                {content.question && <div className="fw-bold fs-6 text-light mb-1">Kérdés: <span className="fw-normal"><LanguageText text={content.question} lang={content.questionLang || 'hu'} parts={content.questionParts} /></span></div>}
                {content.textToTranslate && <div className="fw-bold fs-6 text-light mb-1">Fordítandó: <span lang={content.questionLang} className="fw-normal">{content.textToTranslate}</span></div>}
                {content.sentence && <div className="fw-bold fs-6 text-light mb-1">Mondat: <span lang={content.questionLang} className="fw-normal">{content.sentence}</span></div>}
                {content.hint && <div className="mt-2"><strong>Tipp a fordításhoz: </strong><TranslationHint content={content} /></div>}
            </div>

            {content.options && (
                <div className="mt-2 mb-2">
                    <strong className="text-secondary">Válaszlehetőségek: </strong> 
                    {Array.isArray(content.options) ? (
                        <ul className="mb-0 mt-1 ps-3 text-light">
                            {content.options.map((opt, i) => (
                                <li key={i} lang={content.answerLang}>{typeof opt === 'object' ? opt.text : opt}</li>
                            ))}
                        </ul>
                    ) : <span className="text-light">{content.options}</span>}
                </div>
            )}
            
            {content.words && (
                <div className="mt-2 mb-2">
                    <strong className="text-secondary">Szóbank: </strong> 
                    <div className="d-flex flex-wrap gap-2 mt-1">
                        {content.words.map((w, i) => <Badge lang={content.answerLang} bg="secondary" text="light" key={i} className="px-2 py-1 border border-light border-opacity-25">{w}</Badge>)}
                    </div>
                </div>
            )}

            {/* BIZTONSÁGOS SZEVEROLDALI VÁLASZ */}
            {serverCorrectAnswer && (
                <div className="mt-3 pt-2 border-top border-secondary">
                    <div className="text-success fw-bold fs-6">
                        <span aria-hidden="true">✓ </span>Helyes megoldás: <span lang={content.answerLang} className="fw-normal">{serverCorrectAnswer}</span>
                    </div>
                </div>
            )}
            
        </div>
    );
};

export default ExercisePreviewCard;
