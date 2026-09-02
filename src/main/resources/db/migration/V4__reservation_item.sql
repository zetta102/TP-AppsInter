ALTER TABLE inventory
    ADD COLUMN version BIGINT;

CREATE TABLE reservation_item
(
    uuid           UUID           NOT NULL,
    reservation_id UUID           NOT NULL,
    inventory_id   UUID           NOT NULL,
    product_name   VARCHAR(255)   NOT NULL,
    unit_price     DECIMAL(10, 2) NOT NULL,
    quantity       INTEGER        NOT NULL,
    line_total     DECIMAL(10, 2) NOT NULL,
    CONSTRAINT pk_reservation_item PRIMARY KEY (uuid),
    CONSTRAINT fk_reservation_item_reservation FOREIGN KEY (reservation_id) REFERENCES reservation (uuid) ON DELETE CASCADE,
    CONSTRAINT fk_reservation_item_inventory FOREIGN KEY (inventory_id) REFERENCES inventory (uuid),
    CONSTRAINT uk_reservation_item_product UNIQUE (reservation_id, inventory_id),
    CONSTRAINT ck_reservation_item_quantity CHECK (quantity > 0)
);

CREATE INDEX idx_reservation_item_reservation ON reservation_item (reservation_id);
CREATE INDEX idx_reservation_item_inventory ON reservation_item (inventory_id);
