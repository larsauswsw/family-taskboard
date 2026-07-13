# Prod migration: `admin` column on `app_user`

Needed once, before deploying the family-user-management admin-role feature
(`071a23b` "Add family member (user) management with an admin role").

## Why a manual step is needed

Prod runs with `dbCreate: update` (`grails-app/conf/application.yml`) — there's
no Liquibase/Flyway in this project, Hibernate normally adds new columns on
boot. But `User.admin` is a primitive `boolean` with no SQL default declared
in the domain mapping, so Hibernate generates:

```sql
ALTER TABLE app_user ADD COLUMN admin boolean NOT NULL;
```

On a Postgres table that already has rows (prod's existing `lars` account),
that fails immediately — existing rows would have no value for a NOT NULL
column. The app would crash on boot after pulling the new image.

`BootStrap.ensureAtLeastOneAdmin()` already handles promoting the oldest user
to admin on next boot — no SQL needed for that part. Only the column needs to
be pre-created with a default.

## The fix

Run against the prod Postgres database, before (or immediately after) pulling
the new image:

```bash
docker compose -f docker-compose.prod.yml exec db psql -U "$DB_USER" -d taskboard \
  -c "ALTER TABLE app_user ADD COLUMN IF NOT EXISTS admin boolean NOT NULL DEFAULT false;"
```

Then deploy as usual:

```bash
docker compose -f docker-compose.prod.yml pull
docker compose -f docker-compose.prod.yml up -d
```

Hibernate finds the column already matches and does nothing further;
`ensureAtLeastOneAdmin()` promotes the oldest existing user to admin on that
boot.

## Verifying

```bash
docker compose -f docker-compose.prod.yml exec db psql -U "$DB_USER" -d taskboard \
  -c "SELECT username, admin FROM app_user ORDER BY id;"
```

Exactly one row (the oldest account) should have `admin = t` afterward.
