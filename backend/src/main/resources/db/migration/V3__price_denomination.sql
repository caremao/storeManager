-- Moneda en que se fijó el precio (solo para mostrarlo; la cuenta sigue en cobre). Null = oro.
alter table item add column price_denomination varchar(10);
alter table trade_request add column price_denomination varchar(10);
