# LanguageApp

A LanguageApp nyelvtanulást támogató webalkalmazás, amely az önálló gyakorlást és a tanári feladatkiadást közös felületen biztosítja. A projekt szakdolgozati munka részeként készül.

## Fő funkciók

- **Tanulás:** leckék, többféle feladattípus, dinamikus vagy rögzített nehézség, korábbi leckék ismétlése és szintenkénti mastery-csillagok.
- **Szótár HUB:** mentett szavak, rendezés és szűrés, szókártyák, esedékességhez igazodó dinamikus gyakorlás.
- **Osztálytermek:** meghívásos csatlakozás, tanári feladatok és vizsgák, egyéni időbeállítások, értékelés és haladási statisztikák.
- **Közösség és motiváció:** barátkérelmek, kihívások, ranglisták, XP, napi sorozat és kitüntetések.
- **Fiókkezelés:** e-mailes regisztráció és megerősítés, Google- és Discord-belépés, belépési módok összekapcsolása, profil- és biztonsági beállítások.
- **Akadálymentes használat:** billentyűzetes navigáció, képernyőolvasót támogató jelölések, világos/sötét megjelenés és nagyítható felület.

A tananyag egyes képei jelenleg helykitöltők. Ezek nem minden esetben alkalmasak a képfelismerési feladatok tartalmi értékelésére.

## Technológiák

| Réteg | Technológia |
|---|---|
| Felhasználói felület | React, Vite, Bootstrap / React Bootstrap |
| Szerver | Java 21, Spring Boot, Spring Security, JPA / Hibernate |
| Adatbázis | PostgreSQL 15 |
| Futtatás | Docker Compose, Nginx, helyi HTTPS |
| Külső szolgáltatások | SMTP / Mailtrap, Cloudinary, Google OAuth, Discord OAuth |

## Telepítés Dockerrel

Az alábbi lépések egy saját gépen futó, oktatói kipróbálásra használható példányt állítanak össze. A parancsok Windows PowerShellhez készültek; a Docker a Java-, Node.js- és adatbázis-környezetet is felépíti, ezért ezeket a futtatáshoz nem kell külön telepíteni.

### 1. Előfeltételek

Telepítsd a következőket:

- [Git](https://git-scm.com/downloads).
- [Docker Desktop](https://docs.docker.com/desktop/setup/install/windows-install/), működő Linux-konténeres környezettel.
- [mkcert](https://github.com/FiloSottile/mkcert), a saját gépen megbízható localhost-tanúsítvány létrehozásához.

Indítsd el a Docker Desktopot. Ellenőrizd a terminálban:

```powershell
git --version
docker version
docker compose version
mkcert -version
```

A `docker version` parancsnál a szervernek is elérhetőnek kell lennie. Legyen szabad a 80-as, 443-as, 8080-as és 5432-es port, valamint elegendő hely az image-eknek és a build cache-nek a Docker által használt lemezen is.

### 2. A projekt letöltése

A jelenlegi kipróbálható változat a `feature/app-polishing-and-oauth` ágon található:

```powershell
git clone --branch feature/app-polishing-and-oauth https://github.com/Tofkaa/PTEMIK-Projekt-LanguageLearningApp.git
cd PTEMIK-Projekt-LanguageLearningApp
```

A további parancsokat ebből a gyökérkönyvtárból futtasd. Ha másik kiadás vagy ág kipróbálására kaptál felkérést, annak verzióját használd.

### 3. Környezeti beállítások

Első telepítéskor készíts helyi konfigurációt a [`.env.example`](.env.example) alapján:

```powershell
Copy-Item -LiteralPath .env.example -Destination .env
```

Nyisd meg a létrehozott `.env` fájlt egy szövegszerkesztőben, és töltsd ki a mezőket. Meglévő telepítés `.env` fájlját ne írd felül a mintával.

| Változók | Kitöltés |
|---|---|
| `DB_USER`, `DB_PASSWORD`, `DB_NAME` | Az új, saját adatbázis felhasználója, egyedi jelszava és neve. A Compose ezekkel hozza létre az első adatbázist. |
| `JWT_SECRET` | Saját gépen generált, Base64-kódolt aláírókulcs; generálása lentebb. |
| `FRONTEND_URL` | A mellékelt Dockeres telepítésnél `https://localhost`. Ez az e-mailes linkek és az engedélyezett böngészős eredet alapja. |
| `OAUTH_AUTHORIZATION_BASE_URL` | Ennél a telepítésnél `http://localhost:8080`, a Google/Discord hitelesítés indításához. |
| `SMTP_HOST`, `SMTP_PORT`, `SMTP_USERNAME`, `SMTP_PASSWORD` | A rendelkezésre álló SMTP-szolgáltatás beállításai. Teszteléshez Mailtrap sandbox használható. |
| `CLOUDINARY_URL` | A képfeltöltést biztosító Cloudinary-fiók kapcsolati adata, a mintában jelzett formátumban. |
| `GOOGLE_CLIENT_ID`, `GOOGLE_CLIENT_SECRET` | A Google OAuth-alkalmazás saját vagy külön biztosított azonosítói. |
| `DISCORD_CLIENT_ID`, `DISCORD_CLIENT_SECRET` | A Discord OAuth-alkalmazás saját vagy külön biztosított azonosítói. |
| `VITE_APP_SECURITY_EXERCISE_SALT` | A kliens és a backend meglévő válasz-hash formátumához tartozó nyilvános kompatibilitási érték. A mintában megadott értéket hagyd változatlanul. Ez nem API-kulcs vagy JWT-titok. |

**A külső szolgáltatások oktatói kipróbálásához szükséges hozzáférések a projekt készítőjétől igényelhetők, külön egyeztetéssel.** A publikus repository nem tartalmaz használható szolgáltatói titkokat. Saját szolgáltatói fiókok is beállíthatók; az üres mezők és a mintabeli helykitöltők önmagukban nem adnak működő integrációt.

A JWT-aláírókulcsot az alábbi PowerShell-paranccsal generálhatod:

```powershell
$jwtBytes = New-Object byte[] 64
$jwtGenerator = [Security.Cryptography.RandomNumberGenerator]::Create()
try {
    $jwtGenerator.GetBytes($jwtBytes)
    [Convert]::ToBase64String($jwtBytes)
} finally {
    $jwtGenerator.Dispose()
}
```

A kiírt értéket a saját `.env` fájlod `JWT_SECRET` mezőjébe másold. Telepítésenként saját kulcsot használj; normál újraindításkor nem kell újragenerálni.

Saját OAuth-alkalmazásnál az engedélyezett visszatérési címek:

- Google: `http://localhost:8080/login/oauth2/code/google`
- Discord: `http://localhost:8080/login/oauth2/code/discord`

A frontend címe ettől továbbra is `https://localhost`. A Dockeres futtatáshoz elegendő a gyökér `.env`; a `frontend/.env.example` csak külön Vite-indítás esetén szükséges. A `VITE_` előtagú változók a böngészőnek készülő kódba kerülhetnek, ezért szolgáltatói titkot nem szabad ilyen változóba írni.

### 4. Helyi HTTPS

Minden gépen saját tanúsítványt készíts. Először telepítsd a saját helyi hitelesítőt:

```powershell
mkcert -install
```

Ez a gép tanúsítványtárát módosítja, ezért fogadd el a szükséges rendszerengedélyt. Intézményi gépen ehhez rendszergazdai segítségre lehet szükség. Ezután a projekt gyökerében:

```powershell
New-Item -ItemType Directory -Path ./frontend/ssl -Force | Out-Null
mkcert -cert-file ./frontend/ssl/cert.crt -key-file ./frontend/ssl/cert.key localhost 127.0.0.1 ::1
```

A két fájlt az Nginx csak olvashatóan kapja meg a Compose-konfiguráción keresztül. A tanúsítványokat és a privát kulcsokat ne töltsd fel Gitbe, és az mkcert helyi hitelesítőjének privát kulcsát ne oszd meg. A generálás pontos működését az [mkcert hivatalos leírása](https://github.com/FiloSottile/mkcert) ismerteti.

### 5. Első indítás

Ha az `.env` kitöltése és a tanúsítványok létrehozása kész:

```powershell
docker compose up -d --build
docker compose ps
```

Az első build a letöltések és a fordítás miatt több percig tarthat. A PostgreSQL, a backend és az Nginx/frontend külön konténerben indul. A backend az üres adatbázis sémáját létrehozza, és a csomagban található kezdő tananyagokat és kitüntetéseket betölti; első telepítéshez külön migrációs SQL nem szükséges.

A konténerek elindulása után várd meg a backend indulásának végét:

```powershell
docker compose logs --tail 50 backend
```

Ezután nyisd meg: **[https://localhost](https://localhost)**. A böngészőnek érvényesnek kell elfogadnia a saját gépeden generált tanúsítványt.

### 6. Első használat

1. Regisztrálj saját tesztfiókot; a regisztráció során diák- vagy tanári szerepkör választható.
2. Nyisd meg a megerősítő levelet, és erősítsd meg az e-mail-címet. Mailtrap sandbox esetén a levél a sandbox postaládájában jelenik meg, nem a tényleges címzett postaládájában.
3. Jelentkezz be, és válassz tanulási irányt vagy hozz létre tanári osztálytermet.
4. A tanári és diákfolyamatok közös kipróbálásához használj két saját tesztfiókot, például két külön böngészőben.
5. A Google/Discord gombok a konfigurált szolgáltatói hozzáférésekkel használhatók. Meglévő fiókhoz új belépési mód a **Beállítások → Biztonság** oldalon kapcsolható.

A telepítés új, saját adatbázist használ: nem tölti le a készítő felhasználóit, jelszavait vagy korábbi eredményeit. Általános, előre megadott adminisztrátori belépési adat nincs a telepítési útmutatóban; adminisztrátori bemutatóigényt külön egyeztess a készítővel.

## Leállítás, újraindítás és frissítés

```powershell
# Leállítás az adatvolume megtartásával
docker compose stop

# A meglévő konténerek újraindítása
docker compose start
```

Új forrásverzió átvételéhez előbb készíts adatbázismentést, majd a megfelelő ágon:

```powershell
git pull --ff-only
docker compose up -d --build
```

Az adatbázis névvel ellátott Docker-volume-ban marad meg. A `docker compose down -v` ezt az adatot is törölné, ezért normál leállításhoz vagy frissítéshez ne használd. Már létrehozott adatbázis jelszavát az `.env` átírása önmagában nem módosítja.

## Gyakori indulási problémák

| Jelenség | Ellenőrzés |
|---|---|
| A Docker szerver nem érhető el | Indítsd el a Docker Desktopot, ellenőrizd a Linux-konténeres környezetet és a `docker version` kimenetét. |
| Foglalt port | Állítsd le az ugyanazt a portot használó másik szolgáltatást. A mellékelt címek a változatlan Compose-portokra vonatkoznak. |
| Hiányzó `JWT_SECRET` | A projekt gyökeréből indíts; legyen ott kitöltött `.env` és saját, érvényes Base64-kulcs. |
| Tanúsítványhiba vagy újrainduló frontend | Ellenőrizd a `frontend/ssl/cert.crt` és `cert.key` fájlokat, az `mkcert -install` lépést, majd indítsd újra a böngészőt. |
| Átmeneti 502 induláskor | Várd meg a backend indulását; tartós hibánál nézd meg a `docker compose logs --tail 50 backend frontend` kimenetét. |
| Nem érkezik megerősítő levél | Ellenőrizd az SMTP-beállításokat és a megfelelő Mailtrap sandboxot; a kézbesítésre rövid ideig várni kellhet. |
| Nem működik az OAuth | Ellenőrizd a provider-azonosítókat, a két regisztrált callback címet és a `FRONTEND_URL` értékét. |
| Nem működik a profilképfeltöltés | Ellenőrizd a `CLOUDINARY_URL` szolgáltatói adatait. |
| Docker `input/output error` | Ellenőrizd a Docker lemezének és a rendszermeghajtónak a szabad helyét; ne próbáld az alkalmazás adatvolume-jának törlésével javítani. |

Hiba jelzésekor a hibaüzenetet és a kipróbált verziót add meg; `.env` fájlt, privát kulcsot, jelszót vagy teljes hitelesítési linket ne másolj nyilvános hibajegybe.

## Automatizált ellenőrzések

A [Quality gates](.github/workflows/quality.yml) GitHub Actions workflow négy ellenőrzést futtat:

- `frontend`: függőségaudit, lint, tesztek és build.
- `backend`: runner-tesztek, egységtesztek, izolált PostgreSQL-tesztek, csomagolás és Maven-függőségaudit.
- `container-images`: a három futtatási image sérülékenységvizsgálata és SBOM-készítése.
- `secrets`: titokellenőrzés a Git-történeten és a publikálható fájlokon.

Ezek az alkalmazás kipróbálásához nem igényelnek külön kézi tesztkörnyezetet. A helyi CI-runner használatához Python 3.12+, Java 21 és Docker szükséges; a projekt gyökeréből:

```powershell
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/quality_gate.py unit
python scripts/quality_gate.py integration
```

Az integrációs runner saját eldobható adatbázisokat hoz létre; ne helyettesítsd őket az alkalmazás adatbázisával. A tesztek nem helyettesítik a kézi képernyőolvasós és használhatósági ellenőrzéseket.


## Üzemeltetési diagnosztika

Az API minden kéréshez saját `X-Request-ID` azonosítót ad, a hibaválaszban `requestId` mezővel. Hibajegyhez ezt és a verziót add meg. A kliens által küldött azonosítót a backend nem fogadja el. A backend JSON-naplója nem tartalmaz alkalmazásszintű személyes azonosítókat, bemenetet, SQL-paramétert, tokent vagy nyers kivételüzenetet; a hibakategória és a kódhely segíti a hibakeresést. A PostgreSQL hibákhoz nem írunk normál SQL-utasítást, bindértéket vagy DETAIL sort. Ne kapcsold be az SQL-, bind-, HTTP-body- vagy SMTP-wire debug naplózást személyes adatokkal működő környezetben.

A Nginx hozzáférési naplója csak időt, kérésazonosítót, státuszt, válaszméretet és időtartamot tartalmaz. A hibanapló sorai megmaradnak, de az idézett kérés-/upstream-/headerértékek, klienscímek és queryértékek kiírás előtt redakciót kapnak. A frontend healthcheck ellenőrzi a szűrőfolyamat életét és a Nginx-konfigurációt; `unhealthy` állapotnál ellenőrizd a konténert és a naplókat, majd javítás után hozd újra létre. A naplózási szabályt az image-CI külön szintetikus esetekkel ellenőrzi.

A Compose `local` naplódriver szolgáltatásonként legfeljebb három 10 MiB-os naplófájlt tart, tömörítéssel. Ez méretkorlát, nem garantált napalapú megőrzés. A forgatás a régebbi operatív sorokat felülírhatja; konténer-újralétrehozáskor sincs automatikus naplóarchívum; időtartamhoz kötött vagy távoli archiválást az üzemeltető külön állítson be. A konfiguráció frissítése a konténerek újralétrehozásakor érvényesül, az adatvolume megmarad. A mentéseket és az adminisztrációs alkalmazásnaplót ez nem törli.

A `/actuator/health` és `/actuator/metrics` végpont kizárólag aktuálisan aktív, megerősített adminisztrátori JWT-vel érhető el. Az `env`, `configprops`, `loggers`, dumpok és részletes health-adatok nem publikusak. A metrikák rögzített művelet-/eredménycímkéket használnak, nem felhasználót vagy nyers URL-t. Az alap HTTP URI-címkék száma is korlátozott, és nyers URL nem kerül trace-attribútumba.

| Megfigyelés | Metrika / jelzés |
|---|---|
| HTTP hibák és időtartam | `http.server.requests`, `app.http.completed`; legalább 100 befejezett kérésből legalább 1% 5xx egy mintában `HTTP_5XX` jelzés |
| DB-kapcsolatok és várakozás | `hikaricp.connections.active`, `.pending`, `.acquire`; `app.database.lock.waiters`, `.oldest` (másodperc), `app.database.query.active`, `.oldest`; 5 s feletti aktív querynél `SLOW_QUERY`, várakozó kapcsolat vagy lock esetén `DATABASE_WAIT` |
| Ütközések | `app.persistence.conflicts`, rögzített `CONSTRAINT`, `OPTIMISTIC`, `LOCK` kategóriával |
| Levelezés | `app.mail.queue`, `.active`, `.events`, `app.operation.duration{operation=MAIL}`; 80 várakozó vagy új hiba/visszautasítás esetén `MAIL_QUEUE` / `MAIL_FAILURE` |
| SSE | `app.sse.connections`, `app.operation.active{operation=SSE_SEND}`; 5 másodpercnél hosszabb folyamatban lévő küldésnél `SSE_STALL` |
| Háttérjobok | `app.operation.duration`, `.success.age`, `app.job.item.failures`, `.scan.saturated`; friss siker nélkül 45 s vizsga-, 900 s token-, 3 h kihívás- és 48 h streak-job küszöb után `JOB_STALE` |
| A mérés frissessége | `app.observability.probe.age`, `.failures`; 180 s után `PROBE_STALE` |

A külön mintavevő 60 másodpercenként ellenőriz; a DB-próba csak összesített kapcsolatszámot és lockvárakozási kort olvas, legfeljebb két másodperces SQL-időkorláttal. Már várakozó kapcsolatkészletnél kihagyja az új DB-próbát. A `app.operations.signal` értéke 1 aktív és 0 megszűnt jelzés; az állapotváltozás a naplóba is bekerül. A jobkor az első futásig az indulástól számolódik. A küszöbök kezdeti üzemeltetési jelzések, nem igazolt kapacitás/SLA vagy automatikus javítás; tartós jelzésnél a hibakategóriát, a poolt, a külső szolgáltatót és a jobokat vizsgáld. A HTTP/worker/SSE mérés példányonkénti; a PostgreSQL-aggregátum az azonos adatbázis és szerepkör összes kapcsolatát látja, queryszöveg és szereplők nélkül. Több replikánál minden példányt gyűjteni kell, a közös DB-aggregátumot ne összegezd többször. Külső riasztási címzett/gyűjtő nincs automatikusan telepítve.
