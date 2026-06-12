alter table pente_game
add swapped enum('Y', 'N') not null default 'N';

insert into game_event
(name, site_id, game)
values('Non-Tournament Game', 2, 5);

create table temp_eid(
eid int unsigned not null
);

insert into temp_eid
select eid from game_event where name='Non-Tournament Game'
and site_id=2 and game=5;

create table temp_gid(
gid bigint unsigned not null
);

insert into temp_gid
select max(gid)+1 from pente_game where site_id=2;

insert into pente_game
(gid, site_id, event_id, play_date, timer, rated, initial_time, incremental_time,
player1_pid, player2_pid, player1_rating, player2_rating, winner, player1_type,
player2_type, game, swapped)
select g.gid, 2, e.eid, sysdate(), 'N','N','20','0',22000000000002,
22000000000144,1000,1000,1, 0,0,5,'Y'
from temp_gid g, temp_eid e;

drop table temp_eid;
drop table temp_gid;