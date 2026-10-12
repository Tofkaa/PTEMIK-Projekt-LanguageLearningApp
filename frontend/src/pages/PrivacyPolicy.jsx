import React from 'react';
import { Container, Card, Button } from 'react-bootstrap';
import { useNavigate } from 'react-router-dom';
import ThemeToggle from '../components/ThemeToggle';

const PrivacyPolicy = () => {
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
                <h1 className="fw-bold text-info mb-4">Adatkezelési Tájékoztató (GDPR)</h1>
                <p className="text-secondary mb-4">Utolsó frissítés: 2026. október 12.</p>

                <h4 className="fw-bold text-light mt-4">1. Milyen adatokat gyűjtünk?</h4>
                <p className="opacity-75">
                    A regisztráció és a platform használata során a következő személyes és tevékenységi adatokat kezeljük:
                </p>
                <ul className="opacity-75">
                    <li><strong>Azonosító adatok:</strong> E-mail cím, Felhasználónév, Titkosított (hash-elt) jelszó.</li>
                    <li><strong>Profil adatok:</strong> Generált felhasználói azonosító (Tag), Barátkód, feltöltött profilkép.</li>
                    <li><strong>Tanulási adatok:</strong> Megoldott leckék, pontszámok (XP), hibázott kérdések, napi belépési sorozat (Streak).</li>
                </ul>

                <h4 className="fw-bold text-light mt-4">2. Miért gyűjtjük ezeket az adatokat?</h4>
                <p className="opacity-75">
                    Az adataidat kizárólag a szolgáltatás biztosítása céljából kezeljük. A tanulási eredmények a 
                    dinamikus nehézségi motor (adaptív algoritmus) működéséhez szükségesek. Az e-mail címedet fiók-helyreállításra (jelszó visszaállítás) használjuk.
                </p>

                <h4 className="fw-bold text-light mt-4">3. Kivel osztjuk meg az adataidat? (Nyilvánosság)</h4>
                <p className="opacity-75">
                    Személyes adataidat (e-mail cím, jelszó) soha nem adjuk el, és nem osztjuk meg harmadik féllel. 
                    Ugyanakkor a platform közösségi jellege miatt a <strong>Felhasználóneved, Tag-ed, Profilképed, Streaked és a megszerzett XP-d nyilvánosak</strong>:
                </p>
                <ul className="opacity-75">
                    <li>Láthatóak a globális Ranglistán.</li>
                    <li>Láthatóak a veled egy Osztályteremben lévő tanárok és diákok számára.</li>
                    <li>Láthatóak a Barátlistádon szereplő felhasználók számára.</li>
                </ul>

                <h4 className="fw-bold text-light mt-4">4. Sütik (Cookies) és adattárolás</h4>
                <p className="opacity-75">
                    Az alkalmazás a böngésződ helyi tárhelyét (Local Storage / Session Storage) használja a bejelentkezési token (JWT) 
                    és a téma-beállítások tárolására. Külső nyomkövető sütiket (pl. marketing célú trackerek) <strong>nem</strong> használunk, így az oldal működéséhez 
                    szükséges tárolás nem igényel külön süti-elfogadó sávot.
                </p>

                <h4 className="fw-bold text-light mt-4">5. A Te jogaid (GDPR)</h4>
                <p className="opacity-75">
                    Az Európai Unió Általános Adatvédelmi Rendelete (GDPR) értelmében a Beállítások menüpontban az alábbi jogokat gyakorolhatod:
                </p>
                <ul className="opacity-75">
                    <li><strong>Helyesbítés joga:</strong> Bármikor módosíthatod a neved, jelszavad, e-mail címed vagy profilképed.</li>
                    <li><strong>Adathordozhatósághoz való jog (Letöltés):</strong> Egyetlen kattintással letöltheted a rólad tárolt összes adatodat JSON formátumban.</li>
                    <li><strong>Elfeledtetéshez való jog (Fiók törlése):</strong> Fiókod törlésekor személyes adataid (e-mail, név) azonnal felismerhetetlenné (anonimizálttá) válnak a rendszerben. A statisztikai adataid megmaradnak, de soha többé nem köthetők a személyedhez.</li>
                </ul>
            </Card.Body>
        </Card>
    </Container>
);


};

export default PrivacyPolicy;