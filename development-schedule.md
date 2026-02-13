# Fejlesztési Ütemterv - Language Learning Application

Ez a dokumentum a projekt 13 hetes fejlesztési ütemtervét tartalmazza, amelynek célja a szoftver Proof of Concept (PoC), majd a félév végére a Minimum Viable Product (MVP) állapotba hozása.
Technológiai stack: React.js (Frontend), Spring Boot (Backend), PostgreSQL (Adatbázis), Docker környezetben.
## 1. Fázis: Infrastruktúra és Alapok (1-3. hét)
*Cél: A projekt inicializálása, az adatbázis-kapcsolat kialakítása és az alapvető hitelesítési mechanizmusok megvalósítása.*

- [ ] **1. hét: Projekt Setup és Docker környezet**
  - [ ] Spring Boot backend projekt inicializálása és konfigurálása.
  - [ ] React frontend projekt inicializálása.
  - [ ] `docker-compose.yml` elkészítése a PostgreSQL adatbázis futtatásához .
- [ ] **2. hét: Adatbázis entitások és Domain modell**
  - [ ] A specifikált ER diagram leképezése JPA Entitásokra (User, Lesson, Exercise stb.).
  - [ ] JSONB adattípus leképezése a Java modellben az `exercises` tábla számára.
- [ ] **3. hét: Biztonság és Autentikáció**
  - [ ] Spring Security konfigurálása BCrypt jelszótitkosítással.
  - [ ] Dual Token mechanizmus (JWT Access Token + HttpOnly Refresh Token) implementálása.
  - [ ] `/auth/login`, `/auth/register` és `/auth/refresh` API végpontok elkészítése.

## 2. Fázis: Alapfunkciók és PoC (4-7. hét)
*Cél: A tanulási folyamat alapvető logikájának (core loop) megvalósítása a félidei prezentációra.*

- [ ] **4. hét: Leckék és feladatok háttérlogikája**
  - [ ] Az adatbázis feltöltése tesztadatokkal (alap leckék és JSONB feladatok).
  - [ ] `/lessons` és `/lessons/{id}/exercises` API végpontok fejlesztése.
  - [ ] Feladatkiértékelő és eredménymentő logika (`/lessons/{id}/submit`).
- [ ] **5. hét: Frontend alapok és API integráció**
  - [ ] React Router beállítása az alapvető nézetekhez (Login, Dashboard, Lesson UI).
  - [ ] Axios interceptorok konfigurálása a JWT tokenek automatikus kezelésére.
  - [ ] Regisztrációs és bejelentkezési felület összekötése a backend API-val.
- [ ] **6. hét: Tanulási felület (Lesson Practice UI)**
  - [ ] Interaktív feladatmegoldó felület kialakítása (React Virtual DOM kihasználásával).
  - [ ] A JSONB formátumú feladatok dinamikus megjelenítése a kliens oldalon.
  - [ ] A felhasználói válaszok beküldése és a szerver oldali válasz feldolgozása.
- [ ] **7. hét: Mérföldkő - Proof of Concept (PoC)**
  - [ ] Integrációs tesztelés és kritikus hibák javítása a fő tanulási folyamatban.
  - [ ] **Prezentáció:** A PoC bemutatása (Sikeres bejelentkezés, lecke kiválasztása, feladatmegoldás és eredmény mentése).

## 3. Fázis: Gamifikáció és Osztálytermek (8-11. hét)
*Cél: A felhasználói elköteleződést segítő funkciók és a tanári jogosultságok beépítése.*

- [ ] **8. hét: Gamifikáció és Dashboard**
  - [ ] Felhasználói XP (tapasztalati pont) és Streak (gyakorlási sorozat) számításának megvalósítása.
  - [ ] Diák Dashboard felület felépítése a haladási statisztikák megjelenítésével.
- [ ] **9. hét: Osztályterem modul (Backend)**
  - [ ] Role-Based Access Control (RBAC) ellenőrzések implementálása tanári végpontokhoz.
  - [ ] `/classrooms` végpontok elkészítése (csoport létrehozása, meghívókód generálása).
  - [ ] Diákok csatlakozási logikájának megírása (`classroom_members` kezelése).
- [ ] **10. hét: Osztályterem modul (Frontend)**
  - [ ] Tanári nézet kialakítása (diákok listázása, eredmények nyomon követése).
  - [ ] Diák nézet kialakítása az osztálytermi kód megadásához.
- [ ] **11. hét: Tartalék hét / Extra funkciók, ha az idő engedi**
  - [ ] *Opcionális:* Oxford Dictionary API integráció a feladatokon belüli szómagyarázatokhoz.
  - [ ] *Opcionális:* Barátlista és kihívás (Challenge) modul alapjainak lefektetése.
  - [ ] Az esetleges csúszások behozása.

## 4. Fázis: Véglegesítés és MVP (12-13. hét)
*Cél: Egy stabil, bemutatható, konténerizált Minimum Viable Product szállítása.*

- [ ] **12. hét: UX/UI finomhangolás és hibakezelés**
  - [ ] A reszponzív felület véglegesítése a Figma tervek alapján.
  - [ ] Hibaüzenetek (pl. 401 Unauthorized, 400 Bad Request) felhasználóbarát megjelenítése a frontenden.
- [ ] **13. hét: Mérföldkő - Minimum Viable Product (MVP)**
  - [ ] Docker konténerizáció véglegesítése (Spring Boot, React, Nginx).
  - [ ] Teljes körű (End-to-end) tesztelés a specifikált alapfunkciókon.
  - [ ] **Prezentáció:** Az MVP állapot demonstrálása a félév végi bemutatón.
