TESZTELÉSRE:

Az automatikus quality gate a gyökérből is futtatható (Python 3.12+, Java 21;
az integrációs részhez Docker is szükséges):

```powershell
python -m unittest discover -s scripts -p 'test_*.py'
python scripts/quality_gate.py unit
python scripts/quality_gate.py integration
```

A `unit` mód törli a gyermekfolyamat örökölt teszt-URL-jeit és telepítési
beállításait. Az `integration` egy saját, új, rögzített PostgreSQL 15 image-ből
indított konténert használ véletlen localhost-porton, külön eldobható
adatbázisokkal. Nem fogad meglévő adatbázist vagy konténernevet, nem csatol
alkalmazásvolume-ot, és végül saját konténerét és volume-ját is eltávolítja.
Megszakított CI-run után a GitHub runner megszűnik; helyi gép/terminál kényszerű
leállításakor a `languageapp.quality=disposable` címkéjű maradék konténer kézzel
takarítható. Az alkalmazás saját konténereit ne töröld.

A gate ellenőrzi az integrációs osztályok nyilvántartását, a friss Surefire
XML-eket, a hiányzó tesztosztályokat és a váratlan kihagyásokat. Az integrációs
futásban egyetlen kihagyott teszt is hiba. A régi UTC-próbák frissen létrehozott,
adatmentes sémamásolaton továbbra is `validate` módban futnak. A backend package
ugyanebben a teljes futásban készül. A `.github/workflows/quality.yml` ezt,
valamint a frontend `npm ci`, lint, unit teszt és build lépéseit futtatja push és
pull request esetén; titkos konfigurációt nem igényel. A kapuk nem helyettesítik
a kézi akadálymentességi próbákat, a függőségek biztonsági auditját vagy a
terheléses kapacitásmérést. A GitHub branch protection beállításában külön kell
kötelezővé tenni a `frontend` és `backend` ellenőrzést.

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


Az email-megerősítés, az emailcímcsere és a commit utáni levélindítás integrációs tesztjei külön
`VERIFICATION_TEST_DATABASE_URL` változóval futtatható, kizárólag
`jdbc:postgresql://host:port/languageapp_verification_check_...` nevű, előre
létrehozott, eldobható adatbázison. A teszt újraépíti annak sémáját; az alkalmazás
adatbázisát ne add meg. Külső levelet nem küld, a képtároló és háttérjobok is mockoltak.
A változó beállítása után a `backend` mappából:

```powershell
.\mvnw.cmd '-Dtest=VerificationLifecyclePersistenceTest,EmailChangeLifecyclePersistenceTest' test
```


Az OAuth-fiókok azonosítója a szolgáltató és annak stabil felhasználói azonosítója.
Emailcím-egyezés alapján nincs automatikus fiókösszekötés. A korábbi OAuth-fiók
csak a tárolt szolgáltató/azonosító pár igazolt egyezésekor kap új azonosítókapcsolatot;
az alkalmazásbeli emailcímét ez nem módosítja. Új szolgáltató meglévő fiókhoz
hozzáadására ez a verzió még nem ad külön összekötési felületet.

A helyi, szimulált Google/Discord integrációs teszt a
`OAUTH_IDENTITY_TEST_DATABASE_URL` változóval indítható. Kizárólag előre létrehozott,
eldobható `jdbc:postgresql://host:port/languageapp_oauth_check_...` adatbázist fogad el,
annak sémáját újraépíti. Külső szolgáltatói fiók vagy éles OAuth-titok nem szükséges:

```powershell
.\mvnw.cmd '-Dtest=FederatedIdentityPersistenceTest' test
```
