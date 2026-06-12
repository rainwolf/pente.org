alter table pente_game
add game tinyint unsigned not null default 0;
/* need to add indices as well? */

alter table game_event
add game tinyint unsigned not null default 0;

insert into game_event
(name, site_id, game)
values('Non-Tournament Game', 2, 1);

insert into game_event
(name, site_id, game)
values('Non-Tournament Game', 2, 2);