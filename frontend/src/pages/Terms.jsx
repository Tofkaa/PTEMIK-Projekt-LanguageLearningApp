import React from 'react';
import { Container, Card, Button } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import ThemeToggle from '../components/ThemeToggle';

const Terms = () => {
const navigate = useNavigate();

return (
    <Container className="mt-5 pt-4 pb-5 position-relative" style={{ maxWidth: '900px' }}>
        <div className="position-absolute top-0 end-0 p-3" style={{ zIndex: 1000 }}>
            <ThemeToggle />
        </div>
        
        <Button variant="link" className="text-info text-decoration-none p-0 mb-4 fw-bold" onClick={() => navigate(-1)}>
            ⬅️ Vissza
        </Button>

        <Card className="bg-dark text-light border-secondary shadow-lg rounded-4">
            <Card.Body className="p-4 p-md-5">
                <h1 className="fw-bold text-info mb-4">Általános Szerződési Feltételek (ÁSZF)</h1>
                <p className="text-secondary mb-4">Utolsó frissítés: 2026. október 12.</p>

                <h2 className="h4 fw-bold text-light mt-4">1. A szolgáltatás célja</h2>
                <p className="opacity-75">
                    Ez az alkalmazás egy nyelvtanulást segítő szoftver, amely elsősorban oktatási és demonstrációs célokat szolgál (szakdolgozati projekt). 
                    A platform használata ingyenes. A szolgáltató nem vállal garanciát a tananyagok 100%-os nyelvhelyességéért, illetve a sikeres nyelvvizsgáért.
                </p>

                <h2 className="h4 fw-bold text-light mt-4">2. Felhasználói fiók és regisztráció</h2>
                <p className="opacity-75">
                    A szolgáltatás teljes körű használatához regisztráció szükséges. A regisztráció során valós e-mail cím megadása kötelező. 
                    A fiókod jelszavának titokban tartásáért te felelsz. Tilos más nevében fiókot létrehozni.
                </p>

                <h2 className="h4 fw-bold text-light mt-4">3. Felhasználói magatartás és Fair Play</h2>
                <p className="opacity-75">
                    A közösségi funkciók (Ranglista, Kihívások, Osztálytermek) használata során elvárjuk a tisztességes játékot (Fair Play). Szigorúan tilos:
                </p>
                <ul className="opacity-75">
                    <li>Bármilyen automatizált szoftver, bot vagy szkript használata a pontszámok (XP) manipulálására.</li>
                    <li>Olyan felhasználónév választása, amely másokra nézve sértő, uszító, vagy trágár kifejezéseket tartalmaz.</li>
                    <li>A rendszer sebezhetőségeinek kihasználása (exploit).</li>
                </ul>
                <p className="opacity-75">Ezen szabályok megszegése a fiók azonnali, figyelmeztetés nélküli felfüggesztését vonhatja maga után az adminisztrátorok által.</p>

                <h2 className="h4 fw-bold text-light mt-4">4. Szolgáltatás elérhetősége</h2>
                <p className="opacity-75">
                    Mivel a szoftver kísérleti / szakdolgozati jelleggel üzemel, a szolgáltató fenntartja a jogot, hogy a szolgáltatást 
                    előzetes értesítés nélkül szüneteltesse, módosítsa, vagy az adatbázist karbantartás céljából törölje. 
                    Az adatvesztésből (pl. XP vagy sorozat elvesztése) eredő károkért felelősséget nem vállalunk.
                </p>

                <h2 className="h4 fw-bold text-light mt-4">5. Fiók törlése</h2>
                <p className="opacity-75">
                    A Beállítások menüpontban bármikor jogodban áll a fiókod végleges törlését (anonimizálását) kezdeményezni.
                </p>

            </Card.Body>
        </Card>
    </Container>
);


};

export default Terms;