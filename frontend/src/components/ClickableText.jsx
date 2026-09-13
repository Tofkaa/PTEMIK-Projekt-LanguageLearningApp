import React, { useMemo } from 'react';
import SmartWord from './SmartWord';
import { useAuth } from '../context/AuthContext';


const STOP_WORDS = new Set([
    'the', 'a', 'an', 'is', 'are', 'am', 'was', 'were', 'be', 'been',
    'what', 'which', 'where', 'how', 'who', 'whom', 'whose', 'why',
    'i', 'you', 'he', 'she', 'it', 'we', 'they', 'me', 'him', 'her', 'us', 'them',
    'my', 'your', 'his', 'its', 'our', 'their', 'mine', 'yours', 'hers', 'ours', 'theirs',
    'do', 'does', 'did', 'to', 'of', 'in', 'on', 'at', 'for', 'with', 'about', 'by', 'as', 'and', 'or', 'but', 'if', 'not'
]);
const ClickableText = ({ text, disabled = false, source = 'LESSON', hint = '' }) => {
    const { vocabularyMap } = useAuth(); 

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

    if (!text) return null;

    const wordRegex = /([a-zA-Z]+(?:['’-][a-zA-Z]+)*)/g;
    const parts = text.split(wordRegex);

    return (
        <>
            {parts.map((part, index) => {
                if (part.match(/^[a-zA-Z]+(?:['’-][a-zA-Z]+)*$/)) {
                    const cleanWord = part.toLowerCase();

                 // 1. Is there a hint embedded?
                    const preloaded = hintMap[cleanWord];

                    // 2. MAIN FILTER LOGIC:
                    // If the student already knows the word (SRS >= 3) OR the word is on the Stop-Word list,
                    // AND the there is no provided explicit hint, then PLAIN TEXT.
                    if ((vocabularyMap?.[cleanWord] >= 3 || STOP_WORDS.has(cleanWord)) && !preloaded) {
                        return <span key={index}>{part}</span>;
                    }

                    // 3. If it passed the filter, use SmartWord
                    return (
                        <SmartWord 
                            key={index} 
                            word={part} 
                            queryWord={cleanWord} 
                            disabled={disabled} 
                            source={source} 
                            preloadedTranslation={preloaded} 
                        />
                    );
                }
                return <span key={index}>{part}</span>;
            })}
        </>
    );
};

export default ClickableText;