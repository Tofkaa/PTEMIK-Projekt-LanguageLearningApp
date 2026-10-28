/** Parts are explicit author metadata, accepted only when they reproduce the displayed text exactly. */
export default function LanguageText({ text, lang, parts, focusForeignParts = false }) {
    const valid = Array.isArray(parts) && parts.every(p => typeof p?.text === 'string' && typeof p?.lang === 'string')
        && parts.map(p => p.text).join('') === text;
    return <span lang={lang}>{valid ? parts.map((part, index) => <span key={index} lang={part.lang} tabIndex={focusForeignParts && part.lang !== lang && part.text.trim() ? 0 : undefined}>{part.text}</span>) : text}</span>;
}
