import { Container, Row, Col, Tabs, Tab, Card } from 'react-bootstrap';
import FriendSearch from '../components/FriendSearch';
import PendingRequests from '../components/PendingRequests';
import FriendList from '../components/FriendList';

const Friends = () => {
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
                                defaultActiveKey="list"
                                id="friends-tabs"
                                className="border-bottom border-secondary p-3 pb-0 custom-dark-tabs"
                            >
                                <Tab eventKey="list" title="👥 Barátaim">
                                    <FriendList />
                                </Tab>
                                
                                <Tab eventKey="search" title="🔍 Keresés">
                                    <div className="p-4">
                                        <FriendSearch /> 
                                    </div>
                                </Tab>
                                
                                <Tab eventKey="requests" title="🔔 Kérelmek">
                                    <PendingRequests />
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