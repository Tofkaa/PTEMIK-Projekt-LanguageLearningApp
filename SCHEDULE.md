# Fejlesztési Ütemterv - Language Learning Application

- 2026-02-15: Projekt Setup és Docker környezet (1. hét) - [KÉSZ]
- 2026-02-22: Adatbázis Tervezés és Backend Alapok (2. hét) - [FOLYAMATBAN]
- 2026-03-01: Biztonság és Autentikáció (3. hét)
- 2026-03-08: Alapvető Üzleti Logika és API (4. hét)
- 2026-03-15: Frontend Alapok és Bejelentkezés (5. hét)
- 2026-03-22: Tanulási Felület és Core Loop (6. hét)
- 2026-03-29: Proof of Concept (PoC) Prezentáció (7. hét)
- 2026-04-05: Gamifikáció és Dashboard (8. hét)
- 2026-04-12: Osztályterem Modul - Backend (9. hét)
- 2026-04-19: Osztályterem Modul - Frontend (10. hét)
- 2026-04-26: Admin Panel és Nginx Reverse Proxy (11. hét)
- 2026-05-03: UX/UI Finomhangolás és Hibakezelés (12. hét)
- 2026-05-10: Minimum Viable Product (MVP) és Telepítés (13. hét)

---

## Projekt Setup és Docker környezet
A fejlesztési környezet és a háromrétegű architektúra (kliens-szerver modell) alapjainak lefektetése. Magában foglalja a Spring Boot backend, a React.js frontend és a PostgreSQL adatbázis inicializálását. A rendszer komponensei egy közös `docker-compose.yml` fájl segítségével kerülnek konténerizálásra, amely biztosítja az izolált, platformfüggetlen és stabil lokális fejlesztési környezetet.

## Adatbázis Tervezés és Backend Alapok
Az adatbázis séma kialakítása a specifikált ER diagram alapján. A fő entitások (User, Lesson, Exercise, Progress, Result, stb.) és a köztük lévő relációk pontos leképezése JPA entitásokra UUID alapú azonosítókkal, kiegészítve a JSONB adattípus kezelésével. Ezt követi a Spring Data JPA Repository interfészek és a statisztikai lekérdezések (Projections) létrehozása. A fázis végén lefektetjük a kommunikáció alapjait is: létrehozzuk az API kérések/válaszok szabványosítására szolgáló Payload DTO-kat, valamint beállítunk egy Központi Hibakezelőt (Global Exception Handler), amely az esetleges rendszerhibákat egységes, HTTP státuszkódokkal ellátott JSON válaszokká alakítja.

## Biztonság és Autentikáció
Biztonságos bejelentkezés, regisztráció és Role-Based Access Control (RBAC - Diák, Tanár, Admin) jogosultságkezelés implementálása a Spring Security keretrendszerrel. A rendszer Stateless munkamenetet használ, így a kommunikáció védelmét a Dual Token mechanizmus látja el: egy rövid lejáratú JWT Access Token a kérések fejlécében, és egy HttpOnly Cookie-ban tárolt Refresh Token a munkamenet fenntartásához. A jelszavak védelmét BCrypt titkosítás garantálja.

## Alapvető Üzleti Logika és API
A tanulási folyamatot kiszolgáló RESTful API végpontok (Controllers) és a mögöttes üzleti logika (Service réteg) megírása. Ebben a fázisban valósul meg a leckék és a hozzájuk tartozó feladatok lekérésének algoritmusa, valamint az adatbázis feltöltése kezdeti tesztadatokkal. Szintén itt készül el a felhasználók által beküldött válaszok szerveroldali kiértékelésének, a pontszámításnak és az eredmények biztonságos mentésének logikája.

## Frontend Alapok és Bejelentkezés
A React.js alkalmazás alapstruktúrájának, komponens-hierarchiájának és a globális állapotkezelésnek beállítása. A kliensoldali navigációt a React Router biztosítja. Az API kommunikációhoz Axios interceptorok kerülnek konfigurálásra, amelyek automatikusan csatolják a JWT tokeneket a védett végpontokhoz. Ebben a fázisban készül el a regisztrációs és bejelentkező felület, összekötve a backend szolgáltatásokkal.

## Tanulási Felület és Core Loop
A PoC legfontosabb technikai elemének, az interaktív feladatmegoldó felületnek a teljes körű kialakítása. A React Virtual DOM képességeit kihasználva a JSONB formátumban érkező minta-feladatok (pl. feleletválasztós, fordítás) dinamikusan jelennek meg. Megvalósításra kerül a felhasználói válaszok aszinkron beküldése, az azonnali szerveroldali kiértékelés, a vizuális visszajelzés (helyes/helytelen), és a leckét lezáró eredményösszesítő (Summary) képernyő.

## Proof of Concept (PoC) Prezentáció
A projekt félidei, legfontosabb technikai mérföldköve. Ennek a fázisnak a célja annak demonstrálása, hogy az alkalmazás szoftveres "magja" működőképes és stabil. A PoC során bemutatásra kerül a teljes tanulási életciklus (Core Loop): egy diák sikeresen regisztrál/bejelentkezik, elindít egy rendelkezésre álló minta leckét, interaktívan megoldja a feladatokat, majd a rendszer helyesen kiszámítja és letárolja az eredményeit.

## Gamifikáció és Dashboard
A felhasználói elköteleződést és motivációt segítő funkciók fejlesztése. A backend oldalon megvalósul a tapasztalati pontok (XP) számításának és a napi gyakorlási sorozatok (Streak) nyomon követésének algoritmusa. A frontend oldalon elkészül a letisztult Diák Dashboard, amely vizuálisan jeleníti meg ezeket a személyes statisztikákat, a legutóbbi leckét, valamint a tanuló aktuális haladását.

## Osztályterem Modul - Backend
A tanári jogosultságokhoz kötött csoportkezelés háttérlogikájának megvalósítása. Biztonságos API végpontok készülnek a virtuális osztálytermek létrehozására, a meghívókódok egyedi generálására, valamint a diákok csatlakozási kérelmeinek és a `classroom_members` kapcsolótáblának a menedzselésére.

## Osztályterem Modul - Frontend
A tanári és diák specifikus osztályterem-nézetek (UI) lefejlesztése. A diákok számára egy egyszerű felület készül, ahol meghívókód segítségével csatlakozhatnak új kurzusokhoz. A tanárok számára egy komplexebb menedzsment dashboard épül, ahol listázhatják az osztályaikba jelentkezett diákokat, és áttekinthetik azok aggregált teljesítményét és haladási statisztikáit.

## Admin Panel és Nginx Reverse Proxy
Az alkalmazás felügyeletéhez elengedhetetlen Adminisztrációs felület elkészítése, amely lehetővé teszi a felhasználók listázását, a jogosultságok kezelését és a rendszernaplók (Admin Logs) megtekintését. Rendszerszinten beállításra kerül az Nginx Reverse Proxy szerver, amely egyetlen belépési pontként (Entrypoint) funkcionál: kezeli a CORS problémákat, és megfelelően irányítja (Route) az API kéréseket a Spring Boot felé, a statikus tartalmakat pedig a React kliens felé.

## UX/UI Finomhangolás és Hibakezelés
A felhasználói élmény (UX) és az arculat (UI) végleges optimalizálása a Figma tervek alapján. Szigorú reszponzivitás-ellenőrzés a mobil és asztali nézetekhez. Kliensoldali, robusztus hibakezelés implementálása, amely az API-tól érkező különböző HTTP státuszkódokat (pl. 401 Unauthorized, 404 Not Found, 500 Internal Error) barátságos, magyar nyelvű értesítések formájában kommunikálja a felhasználó felé.

## Minimum Viable Product (MVP) és Telepítés
A projekt végleges, bemutatható MVP állapotba hozása. A teljes szoftvercsomag (Frontend, Backend, Adatbázis, Nginx proxy) konténerizációjának ellenőrzése és finomhangolása. End-to-end (E2E) integrációs tesztelés lefolytatása a kritikus folyamatokon. A fejlesztés lezárásaként elkészül a projekt futtatásához és telepítéséhez szükséges technikai dokumentáció.
