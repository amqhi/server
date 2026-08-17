CREATE TYPE "login_type" AS ENUM (
	'google',
	'apple',
	'normal',
	'microsoft'
);

CREATE TYPE "item_type" AS ENUM (
	'note',
	'file',
	'folder',
	'song',
	'artist',
	'album',
	'alias',
	'photo',
    'link'
);

CREATE TYPE "sync_event_type" as ENUM (
    'create',
    'update',
    'move',
    'soft_delete',
    'delete'
);

CREATE TYPE os_type as ENUM (
    'windows',
    'macos',
    'linux',
    'android',
    'ios',
    'ipados'
    'postmarketos'
);

CREATE TYPE device_type as ENUM (
    'phone',
    'tablet',
    'laptop',
    'desktop',
    'terminal'
);

CREATE TYPE "app_type" AS ENUM (
	'cloud',
	'music',
	'notes',
	'photos',
	'web',
	'ai'
);

CREATE TABLE IF NOT EXISTS "users" (
                                       "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "name" TEXT NOT NULL,
    "password" TEXT,
    "login_type" LOGIN_TYPE,
    "provider_id" TEXT UNIQUE,
    "email" TEXT NOT NULL UNIQUE,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "updated_at" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "deleted_at" TIMESTAMPTZ,
    "birth_date" TIMESTAMPTZ,
    "profile_picture_id" UUID,
    "last_login_at" TIMESTAMPTZ,
    "used_storage" BIGINT NOT NULL DEFAULT 0,
    PRIMARY KEY("id")
    );


CREATE UNIQUE INDEX "users_provider_unique_idx"
    ON "users" ("login_type", "provider_id");

CREATE TABLE IF NOT EXISTS "items" (
                                       "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "user_id" UUID NOT NULL,
    "type" ITEM_TYPE NOT NULL,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "updated_at" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "event_at" TIMESTAMPTZ,
    "deleted_at" TIMESTAMPTZ,
    "parent_id" UUID,
    "name" TEXT,
    "comment" TEXT,
    "encrypted" BOOLEAN DEFAULT false,
    "app_scope" INTEGER NOT NULL DEFAULT 63,
    PRIMARY KEY("id")
    );


CREATE INDEX "items_index_0"
    ON "items" ("parent_id");

CREATE TABLE IF NOT EXISTS "notes" (
                                       "id" UUID NOT NULL UNIQUE,
                                       "content" JSONB NOT NULL,
                                       "extra" JSONB,
                                       "title" TEXT,
                                       "subtitle" TEXT,
                                       "style" JSONB,
                                       PRIMARY KEY("id")
    );




CREATE TABLE IF NOT EXISTS "songs" (
                                       "id" UUID NOT NULL UNIQUE,
                                       "metadata" JSONB NOT NULL,
                                       PRIMARY KEY("id")
    );




CREATE TABLE IF NOT EXISTS "files" (
                                       "id" UUID NOT NULL UNIQUE,
                                       "checksum" TEXT NOT NULL,
                                       "size" BIGINT NOT NULL DEFAULT 0,
                                       "mime_type" TEXT NOT NULL,
                                       PRIMARY KEY("id")
    );




CREATE TABLE IF NOT EXISTS "themes" (
                                        "id" UUID NOT NULL UNIQUE,
                                        "data" JSONB NOT NULL,
                                        "scope" JSONB,
                                        PRIMARY KEY("id")
    );




CREATE TABLE IF NOT EXISTS "albums" (
                                        "id" UUID NOT NULL UNIQUE,
                                        "metadata" JSONB NOT NULL,
                                        PRIMARY KEY("id")
    );




CREATE TABLE IF NOT EXISTS "artists" (
                                         "id" UUID NOT NULL UNIQUE,
                                         "metadata" JSONB NOT NULL,
                                         PRIMARY KEY("id")
    );

CREATE TABLE IF NOT EXISTS "links" (
                                           "id" UUID NOT NULL UNIQUE,
                                           "url" TEXT NOT NULL,
                                           "metadata" JSONB,
                                           PRIMARY KEY("id"),
    FOREIGN KEY("id") REFERENCES "items"("id") ON DELETE CASCADE
    );

CREATE TABLE IF NOT EXISTS "aliases" (
                                         "id" UUID NOT NULL UNIQUE,
                                         "target_id" UUID NOT NULL,
                                         PRIMARY KEY("id")
    );

CREATE TABLE IF NOT EXISTS "browsing_history" (
                                                  "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "user_id" UUID NOT NULL,
    "url" TEXT NOT NULL,
    "title" TEXT NOT NULL,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY("id")
    );


CREATE INDEX "browsing_history_index_0"
    ON "browsing_history" ("created_at", "user_id");

CREATE TABLE IF NOT EXISTS "preferences" (
                                             "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "user_id" UUID NOT NULL,
    "data" JSONB NOT NULL,
    "app_type" APP_TYPE NOT NULL,
    PRIMARY KEY("id")
    );

CREATE TABLE IF NOT EXISTS "user_devices" (
                                              "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "user_id" UUID NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "name" TEXT,
    "os" OS_TYPE,
    "type" DEVICE_TYPE,
    "bit_mask"     INTEGER NOT NULL,
    "registered_at" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "last_active_at" TIMESTAMPTZ NOT NULL DEFAULT NOW()
    );
CREATE TABLE IF NOT EXISTS "tokens" (
                                        "id" UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    "user_id" UUID NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "device_id" UUID NOT NULL REFERENCES "user_devices"("id") ON DELETE CASCADE,
    "value" TEXT NOT NULL UNIQUE,
    "expires_at" TIMESTAMPTZ NOT NULL,
    "created_at" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    "revoked_at" TIMESTAMPTZ,
    "ip_address" TEXT
    );

CREATE TABLE IF NOT EXISTS "shares" (
                                        "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "item_id" UUID NOT NULL,
    "expires_at" TIMESTAMPTZ,
    "password" TEXT,
    "key" TEXT NOT NULL UNIQUE,
    "view_count" INTEGER NOT NULL DEFAULT 0,
    "max_views" INTEGER,
    "created_at" TIMESTAMPTZ DEFAULT NOW(),
    "enabled" BOOLEAN,
    PRIMARY KEY("id")
    );

CREATE TABLE IF NOT EXISTS "sync_events" (
    "id" UUID NOT NULL DEFAULT gen_random_uuid(),
    "user_id" UUID NOT NULL REFERENCES "users"("id") ON DELETE CASCADE,
    "item_id" UUID NOT NULL,
    "type" SYNC_EVENT_TYPE NOT NULL,
    "synced_devices"   INTEGER DEFAULT 0,
    "occurred_at" TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    PRIMARY KEY("id"),
    CONSTRAINT "fk_sync_events_item"
    FOREIGN KEY("item_id") REFERENCES "items"("id")
    ON DELETE CASCADE
    );

ALTER TABLE "albums"
    ADD FOREIGN KEY("id") REFERENCES "items"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "items"
    ADD FOREIGN KEY("user_id") REFERENCES "users"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "notes"
    ADD FOREIGN KEY("id") REFERENCES "items"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "files"
    ADD FOREIGN KEY("id") REFERENCES "items"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "songs"
    ADD FOREIGN KEY("id") REFERENCES "items"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "items"
    ADD FOREIGN KEY("parent_id") REFERENCES "items"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "themes"
    ADD FOREIGN KEY("id") REFERENCES "items"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "artists"
    ADD FOREIGN KEY("id") REFERENCES "items"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "tokens"
    ADD FOREIGN KEY("user_id") REFERENCES "users"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "files"
    ADD FOREIGN KEY("id") REFERENCES "items"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "aliases"
    ADD FOREIGN KEY("id") REFERENCES "items"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "aliases"
    ADD FOREIGN KEY("target_id") REFERENCES "items"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "shares"
    ADD FOREIGN KEY("item_id") REFERENCES "items"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "browsing_history"
    ADD FOREIGN KEY("user_id") REFERENCES "users"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;
ALTER TABLE "preferences"
    ADD FOREIGN KEY("user_id") REFERENCES "users"("id")
        ON UPDATE NO ACTION ON DELETE CASCADE;


CREATE UNIQUE INDEX "items_index_1"
    ON "items" (
                "user_id",
                COALESCE("parent_id", '00000000-0000-0000-0000-000000000000'),
                "name"
        )
    WHERE "deleted_at" IS NULL;

CREATE INDEX "items_user_app_scope_created_at_idx"
    ON "items" ("user_id", "app_scope", "created_at" DESC);