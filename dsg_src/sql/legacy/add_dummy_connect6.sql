

delete from game_event where site_id=2 and game in (13,14,63);
delete from pente_game where site_id=2 and game in (13,14);
delete from dsg_server_game where game in (13,14);

insert into game_event
(name, site_id, game)
values('Live Game', 2, 13);
insert into game_event
(name, site_id, game)
values('Live Game', 2, 14);
insert into game_event
(name, site_id, game)
values('Turn-based Game', 2, 63);


create table temp_max_gid(gid bigint);
insert into temp_max_gid
select max(gid) + 1 from pente_game where site_id = 2
and gid < 40000000000000;

insert into pente_game
(gid, site_id, event_id, round, section, play_date, timer, rated,
 initial_time, incremental_time, player1_pid, player2_pid, player1_rating,
 player2_rating, winner, player1_type, player2_type, game)
select a.gid, 2, b.eid, NULL, NULL, sysdate(), 'N', 'N', 20, 0,
22000000000144, 22000000000145, 1600, 1600, 1, 0, 0, 13
from temp_max_gid a, game_event b
where b.site_id=2 and b.game=13;

insert into pente_game
(gid, site_id, event_id, round, section, play_date, timer, rated,
 initial_time, incremental_time, player1_pid, player2_pid, player1_rating,
 player2_rating, winner, player1_type, player2_type, game)
select a.gid+1, 2, b.eid, NULL, NULL, sysdate(), 'N', 'N', 20, 0,
22000000000144, 22000000000145, 1600, 1600, 1, 0, 0, 14
from temp_max_gid a, game_event b
where b.site_id=2 and b.game=14;

insert into dsg_server_game 
select 1, b.eid, 13 
from game_event b 
where b.game=13 and b.site_id=2;

insert into dsg_server_game 
select 1, b.eid, 14
from game_event b 
where b.game=14 and b.site_id=2;

drop table temp_max_gid;