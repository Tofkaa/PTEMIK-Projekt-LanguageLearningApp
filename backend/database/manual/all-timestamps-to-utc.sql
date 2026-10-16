-- MANUAL migration: back up DB and stop application containers before running.
-- Old offset-less values are interpreted as UTC, as accepted for current test data.
-- Existing timestamptz columns are preserved; SQL date columns are untouched.
BEGIN;
SET LOCAL TIME ZONE 'UTC';
DO $$
DECLARE
    target record;
    existing_type text;
BEGIN
    FOR target IN
        SELECT * FROM (VALUES
            ('admin_logs', 'logged_at'),
            ('assignment_sessions', 'started_at'),
            ('assignment_sessions', 'finished_at'),
            ('challenges', 'start_time'),
            ('challenges', 'end_time'),
            ('challenges', 'completed_at'),
            ('classrooms', 'created_at'),
            ('classroom_assignments', 'available_from'),
            ('classroom_assignments', 'available_until'),
            ('classroom_assignments', 'created_at'),
            ('classroom_members', 'joined_at'),
            ('email_change_tokens', 'expiry_date'),
            ('friendships', 'created_at'),
            ('password_reset_tokens', 'expiry_date'),
            ('password_reset_tokens', 'created_at'),
            ('progress', 'completed_at'),
            ('progress', 'last_attempt_at'),
            ('refresh_tokens', 'expiry_date'),
            ('refresh_tokens', 'created_at'),
            ('results', 'submitted_at'),
            ('student_vocabulary', 'first_seen_at'),
            ('student_vocabulary', 'last_practiced_at'),
            ('student_vocabulary', 'next_practice_at'),
            ('users', 'created_at'),
            ('users', 'last_login'),
            ('users', 'terms_accepted_at'),
            ('user_achievements', 'achieved_at'),
            ('verification_tokens', 'expiry_date'),
            ('verification_tokens', 'created_at')
        ) AS columns_to_migrate(table_name, column_name)
    LOOP
        SELECT data_type INTO existing_type
        FROM information_schema.columns
        WHERE table_schema = 'public'
          AND table_name = target.table_name AND column_name = target.column_name;
        IF existing_type = 'timestamp without time zone' THEN
            EXECUTE format('ALTER TABLE public.%I ALTER COLUMN %I TYPE timestamp(6) with time zone USING %I AT TIME ZONE ''UTC''',
                           target.table_name, target.column_name, target.column_name);
        ELSIF existing_type = 'timestamp with time zone' THEN
            RAISE NOTICE 'Already migrated: %.%', target.table_name, target.column_name;
        ELSE
            RAISE EXCEPTION 'Unexpected or missing type for %.%: %',
                target.table_name, target.column_name, existing_type;
        END IF;
    END LOOP;
END $$;
COMMIT;
