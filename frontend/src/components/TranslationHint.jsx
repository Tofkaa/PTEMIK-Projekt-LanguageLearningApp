import LanguageText from './LanguageText';

/** Preserve legacy hint text; explicit author segments control mixed-language speech. */
export default function TranslationHint({ content }) {
    if (!content?.hint) return null;
    return <LanguageText text={content.hint} lang="hu" parts={content.hintParts} />;
}
