-- Lender Training Portal: dev seed data
-- Loads the five CSVs in /tmp/seed/ into the tables created by V1__init.sql.
-- DEV ONLY: it empties all five tables first, and every user gets the password Password123!

\set ON_ERROR_STOP on

begin;

-- Start clean so the script can be re-run. Parents and children are listed together,
-- so Postgres can truncate them without tripping the foreign keys.
truncate module_completion, enrollment, course_module, course, app_user restart identity;

-- Load parents before children, so every foreign key already has its target row.
\copy app_user (id, email, password_hash, full_name, role, created_at) from '/tmp/seed/app_user.csv' with (format csv, header true)
\copy course (id, title, description, status, trainer_id, version, created_at) from '/tmp/seed/course.csv' with (format csv, header true)
\copy course_module (id, course_id, title, content, sort_order, duration_minutes) from '/tmp/seed/course_module.csv' with (format csv, header true)
\copy enrollment (id, user_id, course_id, status, enrolled_at, completed_at) from '/tmp/seed/enrollment.csv' with (format csv, header true)
\copy module_completion (id, enrollment_id, module_id, completed_at) from '/tmp/seed/module_completion.csv' with (format csv, header true)

-- Replace the placeholder with a real BCrypt hash (a different salt per user).
-- pgcrypto is only needed for this step, so it is removed again afterwards.
create extension if not exists pgcrypto;
update app_user set password_hash = crypt('Password123!', gen_salt('bf', 10));
drop extension pgcrypto;

-- We inserted explicit ids, which does not advance the identity sequences.
-- Without this, the app's first insert would get id 1 and fail with a duplicate key.
select setval(pg_get_serial_sequence('app_user', 'id'),          (select max(id) from app_user));
select setval(pg_get_serial_sequence('course', 'id'),            (select max(id) from course));
select setval(pg_get_serial_sequence('course_module', 'id'),     (select max(id) from course_module));
select setval(pg_get_serial_sequence('enrollment', 'id'),        (select max(id) from enrollment));
select setval(pg_get_serial_sequence('module_completion', 'id'), (select max(id) from module_completion));

commit;

-- Summary
select 'app_user' as table_name, count(*) as row_count from app_user
union all select 'course', count(*) from course
union all select 'course_module', count(*) from course_module
union all select 'enrollment', count(*) from enrollment
union all select 'module_completion', count(*) from module_completion;
