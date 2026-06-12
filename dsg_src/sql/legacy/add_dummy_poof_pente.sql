delete from game_event where name = 'Non-Tournament Game'
and site_id = 2 and game = 4;

insert into game_event
(name, site_id, game)
values('Non-Tournament Game', 2, 4);

create table temp_eid(eid int);
insert into temp_eid
select eid from game_event where name = 'Non-Tournament Game'
and site_id = 2 and game = 4;

create table temp_max_gid(gid bigint);
insert into temp_max_gid
select max(gid) + 1 from pente_game where site_id = 2;

insert into pente_game
(gid, site_id, event_id, round, section, play_date, timer, rated,
 initial_time, incremental_time, player1_pid, player2_pid, player1_rating,
 player2_rating, winner, player1_type, player2_type, game)
select a.gid, 2, b.eid, NULL, NULL, sysdate(), 'N', 'N', 20, 0,
22000000000144, 22000000000145, 1600, 1600, 1, 0, 0, 4
from temp_max_gid a, temp_eid b;

drop table temp_max_gid;
drop table temp_eid;