-- Auditoría de movimientos de stock.
--
-- Hasta acá el stock sólo se fijaba en valor absoluto, así que una diferencia entre lo contado y lo
-- registrado no tenía explicación posible. Cada ajuste queda ahora asentado con su motivo y su autor.
CREATE TABLE inventory_adjustment
(
    uuid            UUID                     NOT NULL,
    inventory_id    UUID                     NOT NULL,
    delta           INTEGER                  NOT NULL,
    resulting_stock INTEGER                  NOT NULL,
    reason          VARCHAR(255)             NOT NULL,
    adjusted_by     VARCHAR(255),
    created_at      TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT pk_inventory_adjustment PRIMARY KEY (uuid),
    CONSTRAINT fk_inventory_adjustment_inventory FOREIGN KEY (inventory_id)
        REFERENCES inventory (uuid) ON DELETE CASCADE,
    -- Un ajuste de cero no describe ningún movimiento y sólo ensuciaría el historial.
    CONSTRAINT ck_inventory_adjustment_delta CHECK (delta <> 0),
    CONSTRAINT ck_inventory_adjustment_resulting_stock CHECK (resulting_stock >= 0)
);

-- El historial se consulta siempre por producto y en orden cronológico inverso.
CREATE INDEX idx_inventory_adjustment_inventory ON inventory_adjustment (inventory_id, created_at DESC);
