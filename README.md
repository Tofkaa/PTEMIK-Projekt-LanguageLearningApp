TESZTELÉSRE:

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

Helyi HTTPS és az oktatói gépeken szükséges tanúsítványgenerálás:
[Helyi HTTPS beállítása](docs/local-https.md).

-root .env tartalma: 
DB_USER=app_user
DB_PASSWORD=app_password
DB_NAME=language_learning_db
VITE_APP_SECURITY_EXERCISE_SALT=L4ngu4ge4ppS3cr3tS4lt2026!

-frontend mappa .env tartalma:
VITE_API_BASE_URL=http://localhost:8080/api
