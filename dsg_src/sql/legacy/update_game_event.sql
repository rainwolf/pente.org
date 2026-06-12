/* This script adds a new column site_id to the game_event table */
/* and updates tables to handle this new column */
/* Be sure to modify the event_id's in the last sql command */
/* to match your local data. */

/* add site column */
alter table game_event
add site_id smallint unsigned not null;

/* update all events initially to iyt */
update game_event
set site_id = 1;

/* update pbem events */
update game_event
set site_id = 3
where name = '1999 PBeM Pente Tournament' or
name = '2000 PBeM Pente Tournament';

/* update non-tournament game for dsg */
update game_event
set site_id = 2
where name = 'Non-Tournament Game';

/* insert non-tournament for pbem */
insert into game_event
(name, site_id)
values('Non-Tournament Game', 3);

/* insert non-tournament for iyt */
insert into game_event
(name, site_id)
values('Non-Tournament Game', 1);

/* switch events for the games at pbem */
/* that are Non-Tournament Games */
/* use appropriate event_id's here */
update pente_game 
set event_id = 38
where event_id = 33
and site_id = 3;