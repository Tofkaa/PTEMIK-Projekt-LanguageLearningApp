# Helyi HTTPS beállítása

Ez a leírás a projekt helyi futtatására és oktatói kipróbálására szolgál.
Éles domain vagy nyilvános tanúsítvány nem szükséges hozzá. Minden gépen saját
fejlesztői hitelesítőt és tanúsítványt kell létrehozni.

Az [mkcert hivatalos útmutatója](https://github.com/FiloSottile/mkcert) alapján
helyi hitelesítőt telepítünk, majd azzal aláírt szervertanúsítványt generálunk.
Az `mkcert -install` tartósan hozzáadja a helyi hitelesítőt a támogatott
tanúsítványtárakhoz. Ezt a gép tulajdonosa tudatosan, külön lépésként végzi el.
Ha a gép házirendje tiltja, kérj segítséget a rendszergazdától.

## Windows: első beállítás

1. Telepítsd az mkcertet. Ha már van Chocolatey, rendszergazdai PowerShellben:

   ```powershell
   choco install mkcert
   ```

   Ha nincs Chocolatey, a hivatalos útmutató Windowsra előre fordított
   binárist és Scoop-telepítést is dokumentál. Telepítés után nyiss új terminált,
   és ellenőrizd: `mkcert -version`.

2. Saját Windows-felhasználóddal telepítsd a helyi hitelesítőt, és fogadd el
   a szükséges rendszerengedélyezési ablakot:

   ```powershell
   mkcert -install
   ```

3. A repository gyökerében generáld a tanúsítványt:

   ```powershell
   ./scripts/New-LocalHttpsCertificate.ps1
   ```

   Ha már van helyi tanúsítványod, például a korábbi önaláírt változat:

   ```powershell
   ./scripts/New-LocalHttpsCertificate.ps1 -ReplaceExisting
   ```

   A script a meglévő fájlokat előbb egy figyelmen kívül hagyott, helyi
   `frontend/ssl/backup-*` könyvtárba másolja. Nem telepít programot és nem
   módosít tanúsítványtárat. Ha PowerShell-házirend blokkolja a scriptet,
   a következő parancsokat kézzel is kiadhatod a repository gyökeréből;
   meglévő SSL-fájljaidról előbb készíts helyi másolatot:

   ```powershell
   New-Item -ItemType Directory -Path ./frontend/ssl -Force
   mkcert -cert-file ./frontend/ssl/cert.crt -key-file ./frontend/ssl/cert.key localhost 127.0.0.1 ::1
   ```

4. Az első teljes indításhoz az `.env` fájlok beállítása után:

   ```powershell
   docker compose up -d --build
   ```

   Már futó rendszernél, csak a HTTPS-beállítás frissítéséhez:

   ```powershell
   docker compose up -d --build --no-deps frontend
   ```

5. Indítsd újra az érintett böngészőt; a beépített böngésző esetén szükség
   lehet a teljes asztali alkalmazás újraindítására is. Nyisd meg:
   [https://localhost](https://localhost). A tanúsítványhibának meg kell szűnnie.
   A frontend új buildjének betöltéséhez egyszer frissítsd az oldalt.

A Docker Compose a `frontend/ssl` mappát csak olvashatóan csatolja az Nginx
`/etc/nginx/ssl` útvonalára. Az SSL-fájlok kimaradnak a Docker buildből.
A tanúsítványok nélküli image önmagában nem indít működő HTTPS-szervert;
a mellékelt Compose-beállítás és a helyben generált fájlok együtt szükségesek.

## Mit kapnak az oktatók?

A repositoryt, a környezeti változók kitöltési útmutatóját és ezt a leírást.
Minden oktató a saját gépén végzi el az egyszeri mkcert-telepítést és generálást.
Tanúsítványhoz nem kell `.example` fájl: a generálási parancs írja le a szükséges
neveket és fájlútvonalakat. A `frontend/ssl/README.md` biztosítja, hogy a mappa
szerepeljen a klónozott projektben.

| Fájl | Gitbe kerül? | Szerep |
|---|---|---|
| `frontend/ssl/README.md` | Igen | Hivatkozás a helyi beállításra |
| `scripts/New-LocalHttpsCertificate.ps1` | Igen | Reprodukálható generálás |
| `frontend/ssl/cert.crt` | Nem | Helyi szervertanúsítvány |
| `frontend/ssl/cert.key` | Nem | Helyi szerver privát kulcsa |
| `frontend/ssl/backup-*` | Nem | Korábbi helyi fájlok másolata |
| mkcert `rootCA-key.pem` | Nem | A helyi hitelesítő privát kulcsa, nem megosztható |

A hitelesítő fájljainak helyét az `mkcert -CAROOT` mutatja. A hitelesítő privát
kulcsa maradjon a gépen; nem kell a projektmappába másolni. Másik gép helyi
futtatásához azon a gépen új hitelesítőt kell generálni. A localhost-tanúsítvány
nem ad automatikusan érvényes HTTPS-t hálózati IP-címhez vagy éles domainhez.

## Ellenőrzés és hibakeresés

```powershell
git check-ignore frontend/ssl/cert.crt frontend/ssl/cert.key
git ls-files frontend/ssl
docker compose logs --tail 30 frontend
```

Az első parancs mindkét helyi fájlt felsorolja; a második csak a megosztható
README-t listázhatja, kulcsot nem. Commit előtt a `git status` és a staged diff
ellenőrzése is szükséges.

Ha továbbra is `ERR_CERT_AUTHORITY_INVALID` látszik, ellenőrizd, hogy az
`mkcert -install` sikeres volt-e ugyanazon a gépen, a frontend újraindult-e,
és a böngésző melyik tanúsítványt kapja. Kizárólag megbízható tanúsítvánnyal
folytasd; a tanúsítvány-ellenőrzés kikapcsolása nem része ennek a beállításnak.
Ha egy speciális böngésző továbbra sem fogadja el a helyi hitelesítőt, annak
saját tanúsítványtárát és támogatott konfigurációját kell ellenőrizni.

## Eltávolítás

Ha már nincs szükséged a fejlesztői hitelesítőbe vetett bizalomra:

```powershell
mkcert -uninstall
```

Ez eltávolítja a telepített bizalmat, a generált fájlokat nem törli.
