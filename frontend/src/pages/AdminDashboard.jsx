import React from 'react';
import { Container, Row, Col, Nav, Tab } from 'react-bootstrap';
import UserManagement from '../components/admin/UserManagement';
import SystemLogs from '../components/admin/SystemLogs';
import CurriculumManager from '../components/admin/CurriculumManager';
import AchievementManager from '../components/admin/AchievementManager';
import ClassroomManager from '../components/admin/ClassroomManager';

const AdminDashboard = () => {
    return (
        <Container fluid className="py-4 text-light" style={{ minHeight: '80vh', maxWidth: '1400px' }}>
            <div className="mb-4 border-bottom border-secondary pb-3">
                <h1 className="h2 fw-bold m-0 text-danger"><span aria-hidden="true">🛡️ </span>Adminisztrációs Központ</h1>
                <p className="text-secondary m-0">Rendszerfelügyelet, tartalomkezelés és moderáció</p>
            </div>
            
            <Tab.Container id="admin-sections" defaultActiveKey="users" mountOnEnter>
                <Row>
                    {/* OLDALSÁV (Sidebar) */}
                    <Col md={3} lg={2} className="mb-4 mb-md-0">
                        <Nav variant="pills" aria-label="Adminisztrációs funkciók" className="flex-column gap-2 custom-admin-nav">
                            <Nav.Item>
                                <Nav.Link eventKey="users" className="fw-bold text-light"><span aria-hidden="true">👥 </span>Felhasználók</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link eventKey="content" className="fw-bold text-light"><span aria-hidden="true">📚 </span>Tananyag CMS</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link eventKey="achievements" className="fw-bold text-light"><span aria-hidden="true">🏆 </span>Kitüntetések</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link eventKey="classrooms" className="fw-bold text-light"><span aria-hidden="true">🏫 </span>Osztálytermek</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link eventKey="logs" className="fw-bold text-light"><span aria-hidden="true">📝 </span>Rendszernapló</Nav.Link>
                            </Nav.Item>
                        </Nav>
                    </Col>

                    {/* FŐ TARTALOM (Content Area) */}
                    <Col md={9} lg={10}>
                        <Tab.Content>
                            
                            <Tab.Pane eventKey="users">
                                <div className="p-4 border border-secondary rounded bg-dark shadow-sm">
                                    <h2 className="h4 text-info fw-bold mb-4">Felhasználók Moderációja</h2>
                                    <UserManagement /> 
                                </div>
                            </Tab.Pane>
                            
                            <Tab.Pane eventKey="content">
                                 <div className="p-4 border border-secondary rounded bg-dark shadow-sm">
                                    <h2 className="h4 text-info fw-bold mb-4">Tananyag Kezelő (CMS)</h2>
                                   <CurriculumManager />
                                </div>
                            </Tab.Pane>

                            <Tab.Pane eventKey="achievements">
                                 <div className="p-4 border border-secondary rounded bg-dark shadow-sm">
                                    <h2 className="h4 text-info fw-bold mb-4">Kitüntetés Kezelő</h2>
                                   <AchievementManager />
                                </div>
                            </Tab.Pane>

                            <Tab.Pane eventKey="classrooms">
                                 <div className="p-4 border border-secondary rounded bg-dark shadow-sm">
                                    <h2 className="h4 text-info fw-bold mb-4">Osztálytermek Moderációja</h2>
                                    <ClassroomManager />
                                </div>
                            </Tab.Pane>

                            <Tab.Pane eventKey="logs">
                                <div className="p-4 border border-secondary rounded bg-dark shadow-sm">
                                    <h2 className="h4 text-danger fw-bold mb-4">Rendszernaplók (Audit Trail)</h2>
                                    <SystemLogs /> 
                                </div>
                            </Tab.Pane>

                        </Tab.Content>
                    </Col>
                </Row>
            </Tab.Container>

            <style>{`
                .custom-admin-nav .nav-link { border-radius: 8px; transition: all 0.2s; }
                .custom-admin-nav .nav-link:hover { background-color: rgba(255,255,255,0.1); }
                .custom-admin-nav .nav-link.active { background-color: var(--bs-danger); color: white !important; box-shadow: 0 4px 6px rgba(0,0,0,0.3); }
            `}</style>
        </Container>
    );
};

export default AdminDashboard;
