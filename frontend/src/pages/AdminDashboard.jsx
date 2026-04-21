import React from 'react';
import { Container, Row, Col, Nav, Tab } from 'react-bootstrap';
import UserManagement from '../components/admin/UserManagement';
import SystemLogs from '../components/admin/SystemLogs';
import CurriculumManager from '../components/admin/CurriculumManager';
import AchievementManager from '../components/admin/AchievementManager';

const AdminDashboard = () => {
    return (
        <Container fluid className="py-4 text-light" style={{ minHeight: '80vh', maxWidth: '1400px' }}>
            <div className="mb-4 border-bottom border-secondary pb-3">
                <h2 className="fw-bold m-0 text-danger">🛡️ Adminisztrációs Központ</h2>
                <p className="text-secondary m-0">Rendszerfelügyelet, tartalomkezelés és moderáció</p>
            </div>
            
            <Tab.Container defaultActiveKey="users">
                <Row>
                    {/* OLDALSÁV (Sidebar) */}
                    <Col md={3} lg={2} className="mb-4 mb-md-0">
                        <Nav variant="pills" className="flex-column gap-2 custom-admin-nav">
                            <Nav.Item>
                                <Nav.Link eventKey="users" className="fw-bold text-light">👥 Felhasználók</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link eventKey="content" className="fw-bold text-light">📚 Tananyag CMS</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link eventKey="achievements" className="fw-bold text-light">🏆 Kitüntetések</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link eventKey="classrooms" className="fw-bold text-light">🏫 Osztálytermek</Nav.Link>
                            </Nav.Item>
                            <Nav.Item>
                                <Nav.Link eventKey="logs" className="fw-bold text-light">📝 Rendszernapló</Nav.Link>
                            </Nav.Item>
                        </Nav>
                    </Col>

                    {/* FŐ TARTALOM (Content Area) */}
                    <Col md={9} lg={10}>
                        <Tab.Content>
                            
                            {/* 1. Felhasználók Modul */}
                            <Tab.Pane eventKey="users">
                                <div className="p-4 border border-secondary rounded bg-dark shadow-sm">
                                    <h4 className="text-info fw-bold mb-4">Felhasználók Moderációja</h4>
                                    <UserManagement /> 
                                </div>
                            </Tab.Pane>
                            
                            {/* 2. Tananyag Modul (Holnap) */}
                            <Tab.Pane eventKey="content">
                                 <div className="p-4 border border-secondary rounded bg-dark shadow-sm">
                                    <h4 className="text-warning fw-bold mb-4">Tananyag Kezelő (CMS)</h4>
                                   <CurriculumManager />
                                </div>
                            </Tab.Pane>

                            {/* 3. Kitüntetések Modul (Holnap) */}
                            <Tab.Pane eventKey="achievements">
                                 <div className="p-4 border border-secondary rounded bg-dark shadow-sm">
                                    <h4 className="text-success fw-bold mb-4">Kitüntetés Kezelő</h4>
                                   <AchievementManager />
                                </div>
                            </Tab.Pane>

                            {/* 4. Osztályterem Modul (4. nap) */}
                            <Tab.Pane eventKey="classrooms">
                                 <div className="p-4 border border-secondary rounded bg-dark shadow-sm">
                                    <h4 className="text-primary fw-bold mb-4">Osztálytermek Moderációja</h4>
                                    <p className="text-secondary">Ide jön a globális osztályterem moderátor (4. nap feladata).</p>
                                </div>
                            </Tab.Pane>

                            {/* 5. Napló Modul */}
                            <Tab.Pane eventKey="logs">
                                <div className="p-4 border border-secondary rounded bg-dark shadow-sm">
                                    <h4 className="text-danger fw-bold mb-4">Rendszernaplók (Audit Trail)</h4>
                                    <SystemLogs /> 
                                    
                                </div>
                            </Tab.Pane>

                        </Tab.Content>
                    </Col>
                </Row>
            </Tab.Container>

            {/* Egy kis CSS a szebb gombokért (ezt később a fő CSS fájlodba is áthelyezheted) */}
            <style>{`
                .custom-admin-nav .nav-link { border-radius: 8px; transition: all 0.2s; }
                .custom-admin-nav .nav-link:hover { background-color: rgba(255,255,255,0.1); }
                .custom-admin-nav .nav-link.active { background-color: var(--bs-danger); color: white !important; box-shadow: 0 4px 6px rgba(0,0,0,0.3); }
            `}</style>
        </Container>
    );
};

export default AdminDashboard;