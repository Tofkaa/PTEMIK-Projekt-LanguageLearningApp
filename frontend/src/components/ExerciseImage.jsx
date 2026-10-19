/** Shared alternative text in the player and teacher preview; never derive it from the correct answer. */
export default function ExerciseImage({ exercise, maxHeight = 250 }) {
    const content = exercise?.content || {};
    const imageUrl = exercise?.imageUrl || content.imageUrl;
    if (!imageUrl) return null;
    const fallback = exercise.type === 'IMAGE_CHOICE'
        ? 'Képfelismerési szókincsfeladat: válaszd ki a képen látható dolog idegen nyelvű nevét.'
        : 'A feladathoz tartozó kép';
    return <div className="mb-3 text-center">
        <img src={imageUrl} alt={content.imageAlt?.trim() || fallback} lang={content.imageAltLang || 'hu'}
            className="img-fluid rounded-4 shadow-sm border p-1"
            style={{ maxHeight: `${maxHeight}px`, objectFit: 'contain' }} />
    </div>;
}
