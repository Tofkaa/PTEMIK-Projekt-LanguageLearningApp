import React, { useMemo, useEffect } from 'react';
import SmartWord from './SmartWord';
import { useAuth } from '../context/AuthContext';

/**
 * STOP_WORDS: A set of common English words (conjunctions, pronouns, auxiliary verbs) 
 * that are not clickable by default. Learning them in the dictionary module 
 * would generate unnecessary noise.
 */
const STOP_WORDS = new Set([
    'the', 'a', 'an', 'is', 'are', 'am', 'was', 'were', 'be', 'been',
    'what', 'which', 'where', 'how', 'who', 'whom', 'whose', 'why',
    'i', 'you', 'he', 'she', 'it', 'we', 'they', 'me', 'him', 'her', 'us', 'them',
    'my', 'your', 'his', 'its', 'our', 'their', 'mine', 'yours', 'hers', 'ours', 'theirs',
    'do', 'does', 'did', 'to', 'of', 'in', 'on', 'at', 'for', 'with', 'about', 'by', 'as', 'and', 'or', 'but', 'if', 'not'
]);

/**
 * ClickableText Component
 * 
 * Responsible for the intelligent processing of lesson questions (or sentences). 
 * It filters out Hungarian words and quoted expressions, making only unknown 
 * English words clickable (SmartWord). It also notifies the parent component 
 * about the learnable (but unclicked) words for the Auto-Add feature.
 * 
 * @param {string} text - The raw text (question) to be processed.
 * @param {boolean} disabled - If true, disables clickability (e.g., in test mode).
 * @param {string} source - Context for the backend (e.g., 'LESSON').
 * @param {string} hint - Optional, preloaded translation pairs (e.g., "apple=alma,pear=körte").
 * @param {function} onWordClick - Callback triggered when the student clicks a SmartWord.
 * @param {function} onWordsFound - Callback that passes all clickable English words to the parent.
 */
const ClickableText = ({ text, disabled = false, source = 'LESSON', hint = '', onWordClick, onWordsFound }) => {
    const { vocabularyMap } = useAuth(); 

    // Process string hints received from the backend into a fast-lookup object
    const hintMap = useMemo(() => {
        const map = {};
        if (hint) {
            hint.split(',').forEach(pair => {
                const [en, hu] = pair.split('=').map(s => s.trim().toLowerCase());
                if (en && hu) map[en] = hu;
            });
        }
        return map;
    }, [hint]);

    // Tokenization (splitting and analysis) of the text
    const tokens = useMemo(() => {
        if (!text) return { result: [], validWords: [] };

        // Step 1: Keep quoted (' " ‘ “) and bracketed expressions together.
        // This prevents splitting phrases like 'Anya' in a sentence like "How do you say 'Anya'?".
        const splitRegex = /(['"‘“][^'"’”]*['"’”]|\([^)]*\))/g;
        const parts = text.split(splitRegex);

        let result = [];
        let validWords = []; // Collects words that the student can learn (for Auto-Add)

        parts.forEach((part, index) => {
            // If the current piece is a quoted or bracketed block, treat it as plain text immediately.
            if (part.match(/^(['"‘“][^'"’”]*['"’”]|\([^)]*\))$/)) {
                result.push({ text: part, isClickable: false, id: `quote-${index}` });
            } else {
                // Step 2: Split normal text into words.
                // The regex intentionally includes Hungarian accented characters so that Hungarian words 
                // remain INTACT and fail the pure English check (isPureEnglishWord) as a whole.
                const wordRegex = /([a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]+(?:['’-][a-zA-ZáéíóöőúüűÁÉÍÓÖŐÚÜŰ]+)*)/g;
                const subParts = part.split(wordRegex);

                subParts.forEach((sub, subIdx) => {
                    // Step 3: Determine if the found word is purely English (contains no Hungarian accents)
                    const isPureEnglishWord = /^[a-zA-Z]+(?:['’-][a-zA-Z]+)*$/.test(sub);

                    if (isPureEnglishWord) {
                        const cleanWord = sub.toLowerCase();
                        const preloaded = hintMap[cleanWord];

                        // MAIN FILTER LOGIC:
                        // If the student already knows the word (SRS >= 3) OR it is on the stop-word list,
                        // AND no explicit hint is provided, render it as plain text.
                        if ((vocabularyMap?.[cleanWord] >= 3 || STOP_WORDS.has(cleanWord)) && !preloaded) {
                            result.push({ text: sub, isClickable: false, id: `w-${index}-${subIdx}` });
                        } else {
                            // The word is a valid, learnable English expression.
                            validWords.push(cleanWord); 
                            result.push({ text: sub, isClickable: true, cleanWord, preloaded, id: `w-${index}-${subIdx}` });
                        }
                    } else {
                        // Punctuation marks, spaces, and accented (Hungarian) words -> Plain text
                        result.push({ text: sub, isClickable: false, id: `w-${index}-${subIdx}` });
                    }
                });
            }
        });

        return { result, validWords };
    }, [text, hintMap, vocabularyMap]);

    // Notify the parent component (LessonPlayer) about the found valid English words.
    // This will be used to send the Auto-Add batch request to the backend.
    useEffect(() => {
        if (onWordsFound && tokens.validWords.length > 0) {
            onWordsFound(tokens.validWords);
        }
    }, [tokens.validWords, onWordsFound]);

    return (
        <>
            {tokens.result.map(token => {
                if (token.isClickable) {
                    return (
                        <SmartWord 
                            key={token.id} 
                            word={token.text} 
                            queryWord={token.cleanWord} 
                            disabled={disabled} 
                            source={source} 
                            preloadedTranslation={token.preloaded} 
                            onWordClick={() => onWordClick && onWordClick(token.cleanWord)}
                        />
                    );
                }
                return <span key={token.id}>{token.text}</span>;
            })}
        </>
    );
};

export default ClickableText;