import { useEffect } from 'react';
import { Link } from 'react-router-dom';
import { useAchievementFeedback } from '../context/AchievementFeedbackContext';

const EMPTY = [];
export default function EarnedAchievements({ awards = EMPTY }) {
    const { presentInline } = useAchievementFeedback();
    useEffect(() => { if (awards?.length) presentInline(awards); }, [awards, presentInline]);
    if (!awards?.length) return null;
    return <section className="border border-info rounded-4 p-3 my-4 text-start" aria-label="Most szerzett kitüntetések">
        <h2 className="h4"><span aria-hidden="true">🏆 </span>Új kitüntetések</h2>
        <ul className="list-unstyled">{awards.map(award => <li key={award.userAchievementId} className="mb-2">
            <span aria-hidden="true">{award.iconUrl} </span><strong>{award.name}</strong>
            <p className="mb-0">{award.description}</p>
        </li>)}</ul>
        <Link className="btn btn-outline-info" to="/profile#achievements">Összes kitüntetésem</Link>
    </section>;
}
