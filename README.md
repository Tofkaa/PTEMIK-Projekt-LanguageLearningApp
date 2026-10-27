TESZTELÉSRE:

Automatizált backendtesztek Java 21-gyel, a `backend` mappából:

```powershell
.\mvnw.cmd test
```

Alapesetben az egységtesztek adatbázis nélkül futnak. A Spring Boot/persistence
tesztek külön bekapcsolhatóak, és csak a tesztosztályukban rögzített nevű,
eldobható PostgreSQL-adatbázist fogadják el. A kihagyott integrációs teszt nem
jelent sikeres adatbázisos ellenőrzést.

Az alkalmazásindítási smoke teszthez az `APPLICATION_SMOKE_TEST_DATABASE_URL`
környezeti változó szükséges, `jdbc:postgresql://host:port/languageapp_smoke_check_...`
alakban. A sémát újraépíti; kizárólag előre létrehozott, eldobható tesztadatbázist
adj meg. A seeder, levélküldő, képtároló, SSE és vizsga/kihívás háttérjobok ebben
a tesztben mockoltak. A további persistence tesztek saját opt-in változóikat és
adatbázisnév-előtagjukat használják a tesztforrásokban.

A backendhez kötelező egy helyben generált JWT-aláírókulcs a gyökér `.env`
`JWT_SECRET` változójában (közvetlen backendindításnál a környezetben).
PowerShellben generálható:

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

A kapott értéket csak a helyi `.env`-be másold. Példakulcs vagy tesztkulcs nem
használható valódi környezetben. Kulcscsere után a korábbi access tokenek
érvénytelenek; új hitelesítés/tokenfrissítés szükséges. Hiányzó, hibás Base64 vagy
32 bájtnál rövidebb kulccsal a backend nem indul el. A `.env.example` nem tartalmaz
használható aláírókulcsot.

A bejelentkezések eszközönként külön munkamenetet kapnak. Kijelentkezés csak az
adott munkamenetet zárja le; az ahhoz kiadott access token sem használható tovább.
Jelszó-visszaállítás az összes korábbi munkamenetet visszavonja. A munkamenethez
nem kötött régi access tokeneket az új backend elutasítja; érvényes refresh cookie
esetén háttérfrissítés, egyébként új belépés szükséges. Az API-válaszok és cookie-k
formátuma változatlan.

Helyi HTTPS és az oktatói gépeken szükséges tanúsítványgenerálás:
[Helyi HTTPS beállítása](docs/local-https.md).

-root .env tartalma: 
DB_USER=app_user
DB_PASSWORD=app_password
DB_NAME=language_learning_db
VITE_APP_SECURITY_EXERCISE_SALT=L4ngu4ge4ppS3cr3tS4lt2026!

-frontend mappa .env tartalma:
VITE_API_BASE_URL=http://localhost:8080/api
