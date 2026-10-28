-- Regla de mesa: si los jugadores pueden sumarse monedas solos (gastar siempre pueden).
alter table campaign add column players_can_add_coins boolean not null default true;
