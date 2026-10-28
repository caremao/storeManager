create table campaign (
    id                 uuid primary key,
    slug               varchar(64)  not null unique,
    name               varchar(120) not null,
    gm_passphrase_hash varchar(100) not null,
    created_at         timestamp(6) with time zone not null
);

-- Tiendas y personajes comparten tabla: ambos tienen items, monedas e historial.
create table inventory (
    id                 uuid primary key,
    campaign_id        uuid         not null references campaign (id) on delete cascade,
    kind               varchar(16)  not null,
    name               varchar(120) not null,
    description        varchar(2000),
    copper             int          not null default 0,
    silver             int          not null default 0,
    gold               int          not null default 0,
    platinum           int          not null default 0,
    -- solo tiendas
    visible_to_players boolean      not null default false,
    requires_approval  boolean      not null default true,
    unlimited_funds    boolean      not null default false,
    sell_markup_pct    int          not null default 100,
    buyback_pct        int          not null default 50,
    -- solo personajes
    player_name        varchar(120),
    access_token       varchar(64) unique,
    version            bigint       not null default 0,
    created_at         timestamp(6) with time zone not null,
    updated_at         timestamp(6) with time zone not null
);
create index idx_inventory_campaign on inventory (campaign_id, kind);

create table item (
    id              uuid primary key,
    inventory_id    uuid         not null references inventory (id) on delete cascade,
    name            varchar(200) not null,
    type            varchar(20)  not null,
    category        varchar(100),
    rarity          varchar(20)  not null,
    description     varchar(100000),
    notes           varchar(2000),
    quantity        int          not null,
    unlimited_stock boolean      not null default false,
    weight          double precision,
    value_cp        bigint,
    srd_index       varchar(100),
    properties      varchar(100000),
    version         bigint       not null default 0,
    created_at      timestamp(6) with time zone not null,
    updated_at      timestamp(6) with time zone not null
);
create index idx_item_inventory on item (inventory_id);

create table trade_request (
    id            uuid primary key,
    campaign_id   uuid         not null references campaign (id) on delete cascade,
    shop_id       uuid         not null references inventory (id) on delete cascade,
    character_id  uuid         not null references inventory (id) on delete cascade,
    -- sin FK: el item puede apilarse, venderse o borrarse; item_name guarda la foto
    item_id       uuid         not null,
    item_name     varchar(200) not null,
    kind          varchar(8)   not null,
    quantity      int          not null,
    -- null: el item no tiene precio y el GM lo fija al aprobar
    unit_price_cp bigint,
    status        varchar(12)  not null,
    note          varchar(500),
    version       bigint       not null default 0,
    created_at    timestamp(6) with time zone not null,
    resolved_at   timestamp(6) with time zone
);
create index idx_trade_campaign on trade_request (campaign_id, status);
create index idx_trade_character on trade_request (character_id);

-- Sin FK a inventory: el historial sobrevive al borrado de tiendas o personajes.
create table history_entry (
    id                   uuid primary key,
    campaign_id          uuid         not null references campaign (id) on delete cascade,
    inventory_id         uuid,
    inventory_name       varchar(120),
    related_inventory_id uuid,
    actor                varchar(120) not null,
    action               varchar(30)  not null,
    entity_name          varchar(200),
    details              varchar(100000),
    created_at           timestamp(6) with time zone not null
);
create index idx_history_campaign on history_entry (campaign_id, created_at);

create table app_session (
    id           uuid primary key,
    token_hash   varchar(64) not null unique,
    campaign_id  uuid        not null references campaign (id) on delete cascade,
    role         varchar(8)  not null,
    character_id uuid references inventory (id) on delete cascade,
    created_at   timestamp(6) with time zone not null,
    expires_at   timestamp(6) with time zone not null
);

create table srd_item (
    srd_index   varchar(100) primary key,
    name        varchar(200) not null,
    source      varchar(16)  not null,
    type        varchar(20)  not null,
    category    varchar(100),
    rarity      varchar(20),
    weight      double precision,
    value_cp    bigint,
    description varchar(100000),
    properties  varchar(100000)
);
create index idx_srd_name on srd_item (name);
