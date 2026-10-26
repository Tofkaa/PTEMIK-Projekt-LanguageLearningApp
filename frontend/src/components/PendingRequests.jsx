/**
 * @file PendingRequests.jsx
 * @description Displays inbound friend requests. Allows the user to accept or reject them.
 * Integrates with the global NotificationContext to update the UI instantly upon action.
 */

import { useState, useEffect, useRef } from 'react';
import { Card, Button, Spinner } from 'react-bootstrap';
import api from '../services/api';
import { useNotifications } from '../context/NotificationContext';
import { formatToLocalDisplay } from '../utils/dateUtils';
import LearningFeedback from './LearningFeedback';
import TimedSuccessFeedback from './TimedSuccessFeedback';


/**
 * @component
 * @returns {React.ReactElement} A list of pending friend requests with action buttons.
 */
const PendingRequests = () => {
    const [requests, setRequests] = useState([]);
    const [loading, setLoading] = useState(true);
    const [message, setMessage] = useState({ type: '', text: '' });
    const messageSequence = useRef(0);
    const isFirstLoad = useRef(true);
    const [busyId, setBusyId] = useState(null);
    const headingRef = useRef(null);
    const restoreFocus = useRef(false);

    useEffect(() => {
        if (restoreFocus.current) {
            headingRef.current?.focus();
            restoreFocus.current = false;
        }
    }, [requests]);
    
    const { notifications, refreshNotifications } = useNotifications();

    useEffect(() => {
        fetchPendingRequests(isFirstLoad.current);
        isFirstLoad.current = false;
    }, [notifications.pendingFriends]);

    const fetchPendingRequests = async (isInitialLoad = false) => {
        if (isInitialLoad) setLoading(true);

        try {
            const response = await api.get('/friendships/requests/pending', {
                params: { _t: Date.now() }
            });
            setRequests(response.data || []);
        } catch (err) {
            console.error("Hiba a kérelmek betöltésekor:", err);
            if (isInitialLoad) setMessage({ type: 'danger', text: 'Nem sikerült betölteni a kérelmeket.' });
        } finally {
            if (isInitialLoad) setLoading(false);
        }
    };

    /**
     * Processes the acceptance or rejection of a friend request.
     * @param {string} friendshipId - The UUID of the request.
     * @param {string} action - 'accept' or 'reject'.
     */
    const handleAction = async (friendshipId, action, button) => {
        if (busyId) return;
        setBusyId(friendshipId);
        try {
            const endpoint = `/friendships/requests/${friendshipId}/${action}`;
            const response = await api.post(endpoint);
            
            setMessage({ type: 'success', text: response.data.message || 'A kérelem feldolgozva.', id: ++messageSequence.current });
            // Only restore focus if it is still in the card that is about to disappear.
            restoreFocus.current = !!button.closest('.card')?.contains(document.activeElement);
            
            // 1. Azonnali UI frissítés a listában
            setRequests(prevRequests => prevRequests.filter(req => req.friendshipId !== friendshipId));
            
            // 2. AZONNALI PÖTTY TÖRLÉS a Navbarról és a fülről!
            if (refreshNotifications) {
                refreshNotifications();
            }
        } catch (err) {
            const errorMsg = err.response?.data?.message || 'Hiba történt a művelet során.';
            setMessage({ type: 'danger', text: errorMsg });
        } finally {
            setBusyId(null);
        }
    };

    if (loading) return <div role="status" className="text-center p-4"><Spinner animation="border" variant="info" aria-hidden="true" /> Barátkérelmek betöltése…</div>;

    return (
        <div className="p-3">
            <LearningFeedback error={message.type === 'danger' ? message.text : ''} />
            <TimedSuccessFeedback message={message.type === 'success' ? message : null}
                focusTarget={headingRef} onDismiss={() => setMessage(current =>
                    current.type === 'success' && current.id === message.id ? { type: '', text: '' } : current)} />
            <h2 ref={headingRef} tabIndex={-1} className="h5 text-light mb-4">Beérkező kérelmek ({requests.length})</h2>

            {(!requests || requests.length === 0) ? (
                <div className="p-5 text-center text-light">
                    <h3 className="h5 mb-3">Minden csendes...</h3>
                    <p>Jelenleg nincsenek függőben lévő barátkérelmeid.</p>
                </div>
            ) : (
                <>
                    {requests.map((request) => (
                        <Card key={request.friendshipId} className="bg-dark border-secondary mb-3 shadow-sm">
                            <Card.Body className="d-flex flex-wrap gap-3 justify-content-between align-items-center">
                                <div>
                                    <h3 className="h6 text-light mb-1 fw-bold">
                                        {request.senderName} <span className="text-info">#{request.senderTag}</span>
                                    </h3>
                                    <small className="text-light">
                                        {formatToLocalDisplay(request.sentAt, false)}
                                    </small>
                                </div>
                                <div className="d-flex flex-wrap gap-2">
                                    <Button aria-label={`Elfogad: ${request.senderName}#${request.senderTag}`} aria-disabled={!!busyId} aria-busy={busyId === request.friendshipId} variant="success" size="sm" className="fw-bold" onClick={(event) => handleAction(request.friendshipId, 'accept', event.currentTarget)}>
                                        Elfogad
                                    </Button>
                                    <Button aria-label={`Elutasít: ${request.senderName}#${request.senderTag}`} aria-disabled={!!busyId} aria-busy={busyId === request.friendshipId} variant="outline-danger" size="sm" onClick={(event) => handleAction(request.friendshipId, 'reject', event.currentTarget)}>
                                        Elutasít
                                    </Button>
                                </div>
                            </Card.Body>
                        </Card>
                    ))}
                </>
            )}
        </div>
    );
};

export default PendingRequests;
