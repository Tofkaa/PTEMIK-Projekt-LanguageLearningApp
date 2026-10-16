# UTC timestamps

All 29 persisted timestamps use Java `Instant` and PostgreSQL `timestamp(6) with time zone`. The API accepts explicit offsets and emits ISO 8601 UTC strings ending in `Z`. Timestamp requests without an offset are rejected. The frontend converts local input to UTC and displays timestamps in the browser timezone; Hungarian display language does not force Budapest time.

`users.last_activity_date` remains SQL `date` / Java `LocalDate`. Streak days are defined in `Europe/Budapest`; reset remains at Budapest 00:05. Challenge cleanup retains its hourly frequency with an explicit UTC schedule. Business logic uses the injected UTC `Clock`. Hibernate creation timestamps and the vocabulary persistence callback also produce Instants.

## Migration and deployment

`all-timestamps-to-utc.sql` handles both the original schema and schemas where some timestamp columns have already been migrated. It interprets old timestamps as UTC and skips existing `timestamptz` columns. This interpretation was accepted for the current disposable test data. For other databases, first verify the original timezone of historical values.

Run only the complete migration below; no preliminary migration is required. It can be rerun safely because already migrated columns are skipped.

From the repository root in PowerShell, check each command succeeds:

```powershell
docker compose stop backend frontend
docker exec language_app_db pg_dump -U app_user -d language_learning_db -Fc -f /tmp/before-full-utc.dump
docker cp language_app_db:/tmp/before-full-utc.dump ./before-full-utc.dump

Get-Content -Raw ./backend/database/manual/all-timestamps-to-utc.sql |
    docker exec -i language_app_db psql -U app_user -d language_learning_db -v ON_ERROR_STOP=1

docker compose up -d --build --no-deps backend frontend
```

Do not start the new backend against the old schema: `ddl-auto=update` must not perform this conversion implicitly. Refresh each browser once after deployment to load the new JavaScript. Later assignment start/end transitions update without reloads. Database backups are ignored by Git.

## Verification

```sql
SELECT data_type, count(*)
FROM information_schema.columns
WHERE table_schema = 'public'
  AND data_type IN ('timestamp without time zone', 'timestamp with time zone', 'date')
GROUP BY data_type;
-- Expected: 29 timestamp with time zone, 1 date, no timestamp without time zone.

SELECT title,
       available_from AT TIME ZONE 'UTC' AS start_utc,
       available_from AT TIME ZONE 'Europe/Budapest' AS start_budapest
FROM public.classroom_assignments WHERE title = 'TimeZoneTest';
-- Expected: 2026-10-16 16:00 UTC / 18:00 Budapest.
```

Check login/refresh, verification and password reset expiry, vocabulary practice, challenge history, admin logs, assignment scheduling and session durations. Compare winter and summer dates and local midnight in different browser timezones.

Availability windows use `[from, until)`. Tokens expire exactly at expiry; vocabulary becomes due exactly at its scheduled instant. Challenge cleanup includes the deadline. SRS/challenge days mean elapsed 24-hour days, not local calendar days. Existing late submission policy and grace periods remain unchanged.

Local input rejects impossible dates and spring DST gaps. For repeated autumn times the browser selects the earlier occurrence, then sends its explicit UTC instant. The API can distinguish either occurrence via offsets. Frontend countdowns are timezone-independent but depend on the device clock.

## Tests

Run backend unit tests without starting Spring or touching the database:

```powershell
cd backend
./mvnw.cmd '-Dtest=*Test' test
```

`BackendApplicationTests` initializes the configured test datasource and may update its schema. Run it against a separate test database, never the app DB before manual migration. `UtcPersistenceTest` only runs when `UTC_TEST_DATABASE_URL` is a JDBC URL whose database name starts with `language_learning_utc_check_`. It overrides its datasource URL and validates the migrated schema without automatic DDL.

Frontend: run `npm exec vitest run` in `frontend`. Date tests are also exercised with `TZ=Europe/Budapest` and `TZ=America/New_York`.

The existing full backend unit suite has three unrelated unused-Mockito-stub errors in `LessonServiceTest` (two cases) and `UserDifficultyCalculatorTest` (one case). The UTC verification run excludes those two unchanged test classes. Run against a separately provisioned and migrated verification database:

```powershell
$env:UTC_TEST_DATABASE_URL = 'jdbc:postgresql://localhost:5432/language_learning_utc_check_example'
./mvnw.cmd '-Dtest=*,!LessonServiceTest,!UserDifficultyCalculatorTest' "-Dspring.datasource.url=$env:UTC_TEST_DATABASE_URL" '-Dspring.jpa.hibernate.ddl-auto=validate' test
```

The migration was checked on an isolated copy of the current schema, including already migrated assignment columns. Spring startup/schema validation and timestamp round-trips passed with UTC, Tokyo and New York database session zones. A second run with a New York JVM timezone also passed. Migration fixture checks preserved microseconds at both DST transitions and the calendar date, and rerunning the script preserved the migrated values.
