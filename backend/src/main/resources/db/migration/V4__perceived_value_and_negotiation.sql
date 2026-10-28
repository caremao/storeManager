-- Valor real (value_cp, lo que sabe un experto) frente a lo que cree el dueño del objeto.
alter table item add column perceived_value_cp bigint;
alter table item add column perceived_denomination varchar(10);
alter table item add column value_revealed boolean not null default true;
-- En una tienda: si acepta ofertas del jugador.
alter table item add column negotiable boolean not null default false;
-- Lo existente queda igual que antes: el dueño conoce el valor.
update item set perceived_value_cp = value_cp, perceived_denomination = price_denomination;

-- Regateo: precio de lista al pedir y quién hizo la última oferta.
alter table trade_request add column list_unit_price_cp bigint;
alter table trade_request add column last_offer_by varchar(8);
update trade_request set list_unit_price_cp = unit_price_cp;
