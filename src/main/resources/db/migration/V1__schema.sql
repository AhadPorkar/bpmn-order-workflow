-- Order-to-cash schema. PostgreSQL 16.

CREATE TABLE products (
    sku         VARCHAR(40)    PRIMARY KEY,
    name        VARCHAR(200)   NOT NULL,
    unit_price  NUMERIC(12, 2) NOT NULL CHECK (unit_price >= 0),
    available   INTEGER        NOT NULL CHECK (available >= 0),
    reserved    INTEGER        NOT NULL DEFAULT 0 CHECK (reserved >= 0)
);

CREATE TABLE orders (
    id                   UUID                     PRIMARY KEY,
    customer_id          VARCHAR(64)              NOT NULL,
    customer_segment     VARCHAR(16)              NOT NULL,
    status               VARCHAR(32)              NOT NULL,
    total_amount         NUMERIC(12, 2)           NOT NULL,
    currency             CHAR(3)                  NOT NULL,
    process_instance_key BIGINT,
    invoice_id           UUID,
    payment_id           VARCHAR(64),
    created_at           TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX ix_orders_customer ON orders (customer_id);

CREATE TABLE order_lines (
    order_id    UUID           NOT NULL REFERENCES orders (id),
    line_no     INTEGER        NOT NULL,
    sku         VARCHAR(40)    NOT NULL REFERENCES products (sku),
    quantity    INTEGER        NOT NULL CHECK (quantity > 0),
    unit_price  NUMERIC(12, 2) NOT NULL,
    PRIMARY KEY (order_id, line_no)
);

-- One row per order and SKU. The primary key makes a repeated reservation fail instead of
-- taking the same stock twice.
CREATE TABLE stock_reservations (
    order_id    UUID                     NOT NULL REFERENCES orders (id),
    sku         VARCHAR(40)              NOT NULL REFERENCES products (sku),
    quantity    INTEGER                  NOT NULL CHECK (quantity > 0),
    status      VARCHAR(16)              NOT NULL,
    created_at  TIMESTAMP WITH TIME ZONE NOT NULL,
    PRIMARY KEY (order_id, sku)
);

CREATE TABLE invoices (
    id            UUID                     PRIMARY KEY,
    order_id      UUID                     NOT NULL UNIQUE REFERENCES orders (id),
    amount        NUMERIC(12, 2)           NOT NULL,
    currency      CHAR(3)                  NOT NULL,
    payment_term  VARCHAR(16)              NOT NULL,
    status        VARCHAR(16)              NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    cancelled_at  TIMESTAMP WITH TIME ZONE
);

-- Transactional outbox: written in the same transaction as the state change it describes.
CREATE TABLE outbox_events (
    id            BIGSERIAL                PRIMARY KEY,
    event_key     VARCHAR(120)             NOT NULL UNIQUE,
    aggregate_id  UUID                     NOT NULL,
    event_type    VARCHAR(64)              NOT NULL,
    payload       TEXT                     NOT NULL,
    created_at    TIMESTAMP WITH TIME ZONE NOT NULL,
    published_at  TIMESTAMP WITH TIME ZONE
);

CREATE INDEX ix_outbox_unpublished ON outbox_events (id) WHERE published_at IS NULL;
CREATE INDEX ix_outbox_aggregate ON outbox_events (aggregate_id);
