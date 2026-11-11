create table cart (
    id           uuid primary key,
    status       varchar(20)    not null,
    total_amount numeric(12, 2) not null,
    currency     varchar(3)     not null,
    created_at   timestamptz    not null,
    updated_at   timestamptz    not null,
    version      bigint         not null
);

-- Snapshot of the products at pre-checkout time (price can change upstream afterwards).
-- (cart_id, sort_order) is unique: items keep the order the user chose, one item per slot.
create table cart_item (
    id          uuid primary key,
    cart_id     uuid           not null references cart (id) on delete cascade,
    sort_order  int            not null,
    slug        varchar(64)    not null,
    name        varchar(120)   not null,
    description varchar(255),
    price       numeric(12, 2) not null,
    currency    varchar(3)     not null,
    constraint uk_cart_item_cart_sort_order unique (cart_id, sort_order)
);

-- Payment methods offered to this cart: checkout only accepts one of these.
create table cart_payment_method (
    cart_id    uuid        not null references cart (id) on delete cascade,
    sort_order int         not null,
    type       varchar(20) not null,
    name       varchar(60) not null,
    primary key (cart_id, sort_order)
);

-- No column for card number or CVV, by design.
create table payment (
    id             uuid primary key,
    cart_id        uuid           not null references cart (id),
    method_type    varchar(20)    not null,
    method_name    varchar(60)    not null,
    status         varchar(20)    not null,
    amount         numeric(12, 2) not null,
    currency       varchar(3)     not null,
    last_digits    varchar(4),
    pix_code       varchar(255),
    decline_reason varchar(60),
    processed_at   timestamptz    not null
);

create index ix_payment_cart on payment (cart_id);

-- Database-level guarantee: a cart can be approved only once.
create unique index ux_payment_one_approved_per_cart on payment (cart_id) where status = 'APPROVED';

create table outbox_event (
    id           uuid primary key,
    aggregate_id uuid        not null,
    event_type   varchar(60) not null,
    payload      jsonb       not null,
    created_at   timestamptz not null,
    published_at timestamptz,
    version      bigint      not null
);

create index ix_outbox_unpublished on outbox_event (created_at) where published_at is null;
