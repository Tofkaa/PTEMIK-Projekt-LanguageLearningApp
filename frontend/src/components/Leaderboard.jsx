/**
 * @file Leaderboard.jsx
 * @description Displays the Global or Friends leaderboard with toggleable sorting (XP / Streak).
 * Calculates user levels dynamically based on the global XP formula (XP / 100).
 */

import { useId, useState, useEffect } from 'react';
import { Card, Table, Spinner, Alert, ButtonGroup, ToggleButton, Badge } from 'react-bootstrap';
import api from '../services/api';
import { useAuth } from '../context/AuthContext';

/**
 * Leaderboard component to display user rankings.
 * * @param {Object} props - Component properties.
 * @param {string} [props.defaultScope='global'] - The default scope of the leaderboard ('global' or 'friends').
 * @returns {JSX.Element} The rendered Leaderboard component.
 */
const Leaderboard = ({defaultScope = 'global'}) => {
    const { user } = useAuth();
    const controlId = useId();
    
    const [scope, setScope] = useState(defaultScope);
    const [sortBy, setSortBy] = useState('xp');
    
    const [players, setPlayers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        let active = true;
        const fetchLeaderboard = async () => {
        setLoading(true);
        setError('');
        try {
            const response = await api.get(`/leaderboard/${scope}`, {
                params: { sortBy: sortBy, _t: Date.now() }
            });
            if (active) setPlayers(response.data || []);
        } catch (err) {
            console.error(err);
            if (active) setError('Nem sikerült betölteni a ranglistát.');
        } finally {
            if (active) setLoading(false);
        }
        };
        fetchLeaderboard();
        return () => { active = false; };
    }, [scope, sortBy]);

    /**
     * Calculates the user's level based on their total XP.
     * Matches the universal level calculation formula used across the application.
     * * @param {number} xp - The user's total XP.
     * @returns {number} The calculated level.
     */
    const calculateLevel = (xp) => {
        return Math.floor((xp || 0) / 100) + 1;
    };

    /**
     * Returns an appropriate icon or formatted string based on the user's rank.
     * Displays medals for the top 3 positions.
     * * @param {number} rank - The user's position on the leaderboard.
     * @returns {JSX.Element} The rendered rank indicator.
     */
    const getRankIcon = (rank) => {
        if (rank >= 1 && rank <= 3) return <><span className="visually-hidden">{rank}. hely</span><span aria-hidden="true" className="fs-4">{['🥇', '🥈', '🥉'][rank - 1]}</span></>;
        return <span className="text-secondary fw-bold px-2">{rank}.</span>;
    };

    return (
        <Card className="bg-dark border-secondary shadow-lg mt-4 mb-5">
            <Card.Header className="border-bottom border-secondary d-flex flex-column flex-md-row justify-content-between align-items-center p-3 gap-3">
                <h2 className="h4 text-light mb-0 fw-bold"><span aria-hidden="true">🏆 </span>Ranglista</h2>
                
                <div className="d-flex gap-2 flex-wrap justify-content-center">
                    <fieldset className="border-0 p-0 m-0"><legend className="visually-hidden">Ranglista köre</legend><ButtonGroup size="sm">
                        <ToggleButton
                            id={`${controlId}-global`} type="radio" variant="outline-info" name={`${controlId}-scope`}
                            value="global" checked={scope === 'global'} onChange={(e) => setScope(e.currentTarget.value)}
                        >
                            <span aria-hidden="true">🌍 </span>Globális
                        </ToggleButton>
                        <ToggleButton
                            id={`${controlId}-friends`} type="radio" variant="outline-info" name={`${controlId}-scope`}
                            value="friends" checked={scope === 'friends'} onChange={(e) => setScope(e.currentTarget.value)}
                        >
                            <span aria-hidden="true">🤝 </span>Barátok
                        </ToggleButton>
                    </ButtonGroup></fieldset>

                    <fieldset className="border-0 p-0 m-0"><legend className="visually-hidden">Ranglista rendezése</legend><ButtonGroup size="sm">
                        <ToggleButton
                            id={`${controlId}-xp`} type="radio" variant="outline-warning" name={`${controlId}-sort`}
                            value="xp" checked={sortBy === 'xp'} onChange={(e) => setSortBy(e.currentTarget.value)}
                        >
                            <span aria-hidden="true">⭐ </span>XP
                        </ToggleButton>
                        <ToggleButton
                            id={`${controlId}-streak`} type="radio" variant="outline-warning" name={`${controlId}-sort`}
                            value="streak" checked={sortBy === 'streak'} onChange={(e) => setSortBy(e.currentTarget.value)}
                        >
                            <span aria-hidden="true">🔥 </span>Streak
                        </ToggleButton>
                    </ButtonGroup></fieldset>
                </div>
            </Card.Header>

            <Card.Body className="p-0 position-relative" style={{ minHeight: '300px' }}>
                {loading && (
                    <div role="status" className="position-absolute top-50 start-50 translate-middle text-center">
                        <Spinner animation="border" variant="info" aria-hidden="true" /> Ranglista betöltése…
                    </div>
                )}
                
                {error && <Alert variant="danger" className="m-3">{error}</Alert>}

                {!loading && !error && players.length === 0 && (
                    <div className="text-center p-5 text-light opacity-75">
                        Nincs megjeleníthető adat a ranglistán.
                    </div>
                )}

                {!loading && !error && players.length > 0 && (
                    <div className="table-responsive" role="region" tabIndex={0}
                        aria-label={`${scope === 'global' ? 'Globális' : 'Barátok'} ranglista, ${sortBy === 'xp' ? 'XP' : 'napi sorozat'} szerint, görgethető táblázat`}>
                        <Table size="sm" hover variant="dark" className="leaderboard-table mb-0 text-center align-middle">
                            <caption className="visually-hidden">{scope === 'global' ? 'Globális' : 'Barátok'} ranglista, {sortBy === 'xp' ? 'XP' : 'napi sorozat'} szerint</caption>
                            <thead className="text-uppercase text-secondary" style={{ fontSize: '0.85rem' }}>
                                <tr>
                                    <th scope="col" className="py-3 border-secondary">Hely.</th>
                                    <th scope="col" className="text-start py-3 border-secondary">Játékos</th>
                                    <th scope="col" className="py-3 border-secondary">Szint</th>
                                    <th scope="col" className="py-3 border-secondary">{sortBy === 'xp' ? 'Összes XP' : 'Napi Sorozat'}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {players.map((player) => {
                                    const isMe = user?.userId === player.userId;
                                    
                                    return (
                                        <tr key={player.userId} className={isMe ? 'table-active' : ''}>
                                            <td className="fw-bold">{getRankIcon(player.rank)}</td>
                                            <th scope="row" className="text-start fw-normal">
                                                <span className={`fw-bold ${isMe ? 'text-info' : 'text-light'}`}>
                                                    {player.name}
                                                </span>
                                                <span className="text-secondary ms-1" style={{ fontSize: '0.85rem' }}>
                                                    #{player.userTag}
                                                </span>
                                                {isMe && <Badge bg="info" className="ms-2">Te</Badge>}
                                            </th>
                                            <td>
                                                <Badge bg="secondary" pill className="leaderboard-level px-3 py-2 border border-secondary">
                                                    {calculateLevel(player.xp)}. szint
                                                </Badge>
                                            </td>
                                            <td className="fw-bold text-warning fs-5">
                                                {sortBy === 'xp' ? `${player.xp} XP` : `${player.streak} nap`}
                                            </td>
                                        </tr>
                                    );
                                })}
                            </tbody>
                        </Table>
                    </div>
                )}
            </Card.Body>
        </Card>
    );
};

export default Leaderboard;
