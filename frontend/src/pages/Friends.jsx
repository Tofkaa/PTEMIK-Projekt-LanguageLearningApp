/**
 * @file Friends.jsx
 * @description Main community hub component. Renders a tabbed interface allowing users
 * to manage friends, search for users, view pending requests, and track challenges.
 * Handles client-side "read receipt" logic via LocalStorage.
 */

import { Container, Row, Col, Tabs, Tab, Card, Badge } from 'react-bootstrap';
import FriendSearch from '../components/FriendSearch';
import PendingRequests from '../components/PendingRequests';
import FriendList from '../components/FriendList';
import ActiveChallenges from '../components/ActiveChallenges';
import ChallengeHistory from '../components/ChallengeHistory';
import { useNotifications } from '../context/NotificationContext';
import { useState, useEffect } from 'react';

/**
 * @component
 * @returns {React.ReactElement} The Community/Friends dashboard view.
 */
const Friends = () => {
    const {notifications} = useNotifications();
    const [activeTab, setActiveTab] = useState('list');

    
    const seenFriendsCount = parseInt(localStorage.getItem('seenFriendsCount') || '0');
    const seenHistoryCount = parseInt(localStorage.getItem('seenHistoryCount') || '0');

    const hasNewFriend = notifications.totalFriends > seenFriendsCount;
    const hasNewHistory = notifications.totalHistory > seenHistoryCount;


    const handleTabSelect = (key) => {
        setActiveTab(key);
        
     
        if (key === 'list' && notifications?.totalFriends != null) {
            localStorage.setItem('seenFriendsCount', notifications.totalFriends.toString());
        }
        if (key === 'history' && notifications?.totalHistory != null) {
            localStorage.setItem('seenHistoryCount', notifications.totalHistory.toString());
        }
    };

 
    useEffect(() => {
        if (activeTab === 'list' && notifications?.totalFriends != null) {
            localStorage.setItem('seenFriendsCount', notifications.totalFriends.toString());
        }
        if (activeTab === 'history' && notifications?.totalHistory != null) {
            localStorage.setItem('seenHistoryCount', notifications.totalHistory.toString());
        }
    }, [notifications?.totalFriends, notifications?.totalHistory, activeTab]);
    return (
        <Container className="py-5">
            <Row className="justify-content-center">
                <Col md={10} lg={8}>
                    <div className="d-flex justify-content-between align-items-center mb-4">
                        <h2 className="text-light fw-bold m-0">🌐 Közösség</h2>
                    </div>
                    
                    <Card className="bg-dark border-secondary shadow-lg">
                        <Card.Body className="p-0">
                            <Tabs
                                id="friends-tabs"
                                className="border-bottom border-secondary p-3 pb-0 custom-dark-tabs"
                                activeKey={activeTab}
                                onSelect={handleTabSelect}
                            >
                                {/* FRIENDS TAB */}
                                <Tab eventKey="list" title={
                                    <span>
                                        👥 Barátaim
                                        {hasNewFriend && activeTab !== 'list' && (
                                            <Badge bg="info" className="ms-2">Új!</Badge> // Info színű, mert ez nem egy elvégzendő feladat
                                        )}
                                    </span>
                                }>
                                    <FriendList />
                                </Tab>
                                
                                {/* FRIENDS SEARCH TAB */}
                                <Tab eventKey="search" title="🔍 Keresés">
                                    <div className="p-4">
                                        <FriendSearch /> 
                                    </div>
                                </Tab>
                                
                                {/* REQUESTS TAB */}
                                <Tab eventKey="requests" title={
                                        <span>
                                            🔔 Kérelmek
                                            {notifications.pendingFriends > 0 && (
                                                <Badge bg="danger" pill className="ms-2">{notifications.pendingFriends}</Badge>
                                            )}
                                        </span>
                                    }>
                                    
                                    <div className="p-4">
                                        <PendingRequests />
                                    </div>
                                </Tab>
                                
                                {/* CHALLENGES TAB */}
                                <Tab eventKey="active" title={
                                        <span>
                                            ⚔️ Aktív Kihívások
                                            {notifications.pendingChallenges > 0 && (
                                                <Badge bg="danger" pill className="ms-2">{notifications.pendingChallenges}</Badge>
                                            )}
                                        </span>
                                    }>
                                  
                                    <div className="p-4">
                                        <ActiveChallenges />
                                    </div>
                                </Tab>

                                {/* HISTORY TAB */}
                                <Tab eventKey="history" title={
                                    <span>
                                        📜 Előzmények
                                        {hasNewHistory && activeTab !== 'history' && (
                                            <Badge bg="info" className="ms-2">Új!</Badge>
                                        )}
                                    </span>
                                }>
                                    <ChallengeHistory />
                                </Tab>
                            </Tabs>
                        </Card.Body>
                    </Card>
                </Col>
            </Row>
        </Container>
    );
};

export default Friends;