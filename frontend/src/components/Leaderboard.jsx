/**
 * @file Leaderboard.jsx
 * @description Displays the Global or Friends leaderboard with toggleable sorting (XP / Streak).
 * Calculates user levels dynamically based on the global XP formula (XP / 100).
 */

import { useState, useEffect } from 'react';
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
    
    const [scope, setScope] = useState(defaultScope);
    const [sortBy, setSortBy] = useState('xp');
    
    const [players, setPlayers] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');

    useEffect(() => {
        fetchLeaderboard();
    // eslint-disable-next-line react-hooks/exhaustive-deps
    }, [scope, sortBy]);

    /**
     * Fetches the leaderboard data from the API based on the selected scope and sorting criteria.
     */
    const fetchLeaderboard = async () => {
        setLoading(true);
        setError('');
        try {
            const response = await api.get(`/leaderboard/${scope}`, {
                params: { sortBy: sortBy, _t: new Date().getTime() }
            });
            setPlayers(response.data || []);
        } catch (err) {
            console.error(err);
            setError('Nem sikerült betölteni a ranglistát.');
        } finally {
            setLoading(false);
        }
    };

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
        if (rank === 1) return <span className="fs-4">🥇</span>;
        if (rank === 2) return <span className="fs-4">🥈</span>;
        if (rank === 3) return <span className="fs-4">🥉</span>;
        return <span className="text-secondary fw-bold px-2">{rank}.</span>;
    };

    return (
        <Card className="bg-dark border-secondary shadow-lg mt-4 mb-5">
            <Card.Header className="border-bottom border-secondary d-flex flex-column flex-md-row justify-content-between align-items-center p-3 gap-3">
                <h4 className="text-light mb-0 fw-bold">🏆 Ranglista</h4>
                
                <div className="d-flex gap-2 flex-wrap justify-content-center">
                    <ButtonGroup size="sm">
                        <ToggleButton
                            id="toggle-global" type="radio" variant="outline-info" name="scope"
                            value="global" checked={scope === 'global'} onChange={(e) => setScope(e.currentTarget.value)}
                        >
                            🌍 Globális
                        </ToggleButton>
                        <ToggleButton
                            id="toggle-friends" type="radio" variant="outline-info" name="scope"
                            value="friends" checked={scope === 'friends'} onChange={(e) => setScope(e.currentTarget.value)}
                        >
                            🤝 Barátok
                        </ToggleButton>
                    </ButtonGroup>

                    <ButtonGroup size="sm">
                        <ToggleButton
                            id="toggle-xp" type="radio" variant="outline-warning" name="sortBy"
                            value="xp" checked={sortBy === 'xp'} onChange={(e) => setSortBy(e.currentTarget.value)}
                        >
                            ⭐ XP
                        </ToggleButton>
                        <ToggleButton
                            id="toggle-streak" type="radio" variant="outline-warning" name="sortBy"
                            value="streak" checked={sortBy === 'streak'} onChange={(e) => setSortBy(e.currentTarget.value)}
                        >
                            🔥 Streak
                        </ToggleButton>
                    </ButtonGroup>
                </div>
            </Card.Header>

            <Card.Body className="p-0 position-relative" style={{ minHeight: '300px' }}>
                {loading && (
                    <div className="position-absolute top-50 start-50 translate-middle">
                        <Spinner animation="border" variant="info" />
                    </div>
                )}
                
                {error && <Alert variant="danger" className="m-3">{error}</Alert>}

                {!loading && !error && players.length === 0 && (
                    <div className="text-center p-5 text-light opacity-75">
                        Nincs megjeleníthető adat a ranglistán.
                    </div>
                )}

                {!loading && !error && players.length > 0 && (
                    <div className="table-responsive">
                        <Table table-sm hover variant="dark" className="mb-0 text-center align-middle">
                            <thead className="text-uppercase text-secondary" style={{ fontSize: '0.85rem' }}>
                                <tr>
                                    <th className="py-3 border-secondary">Hely.</th>
                                    <th className="text-start py-3 border-secondary">Játékos</th>
                                    <th className="py-3 border-secondary">Szint</th>
                                    <th className="py-3 border-secondary">{sortBy === 'xp' ? 'Összes XP' : 'Napi Sorozat'}</th>
                                </tr>
                            </thead>
                            <tbody>
                                {players.map((player) => {
                                    const isMe = user?.userId === player.userId;
                                    
                                    return (
                                        <tr key={player.userId} className={isMe ? 'table-active' : ''}>
                                            <td className="fw-bold">{getRankIcon(player.rank)}</td>
                                            <td className="text-start">
                                                <span className={`fw-bold ${isMe ? 'text-info' : 'text-light'}`}>
                                                    {player.name}
                                                </span>
                                                <span className="text-secondary ms-1" style={{ fontSize: '0.85rem' }}>
                                                    #{player.userTag}
                                                </span>
                                                {isMe && <Badge bg="info" className="ms-2">Te</Badge>}
                                            </td>
                                            <td>
                                                <Badge bg="secondary" pill className="px-3 py-2 border border-secondary">
                                                    Lvl {calculateLevel(player.xp)}
                                                </Badge>
                                            </td>
                                            <td className="fw-bold text-warning fs-5">
                                                {sortBy === 'xp' ? `${player.xp} ⭐` : `${player.streak} 🔥`}
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