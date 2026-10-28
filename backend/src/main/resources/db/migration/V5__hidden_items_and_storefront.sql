-- Objetos de una tienda que solo ve el GM (los menciona en la mesa y los vende directo).
alter table item add column hidden boolean not null default false;

-- Token del QR de la vitrina pública de cada tienda (128 bits, como el de los personajes).
alter table inventory add column public_token varchar(64);
update inventory set public_token = replace(cast(random_uuid() as varchar), '-', '') where kind = 'SHOP';
create unique index uq_inventory_public_token on inventory (public_token);
