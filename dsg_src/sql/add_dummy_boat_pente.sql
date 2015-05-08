

delete from game_event where site_id=2 and game in (15,16,65);
delete from pente_game where site_id=2 and game in (15,16);
delete from dsg_server_game where game in (15,16);

insert into game_event
(name, site_id, game)
values('Live Game', 2, 15);
insert into game_event
(name, site_id, game)
values('Live Game', 2, 16);
insert into game_event
(name, site_id, game)
values('Turn-based Game', 2, 65);


create table temp_max_gid(gid bigint);
insert into temp_max_gid
select max(gid) + 1 from pente_game where site_id = 2
and gid < 40000000000000;

insert into pente_game
(gid, site_id, event_id, round, section, play_date, timer, rated,
 initial_time, incremental_time, player1_pid, player2_pid, player1_rating,
 player2_rating, winner, player1_type, player2_type, game)
select a.gid, 2, b.eid, NULL, NULL, sysdate(), 'N', 'N', 20, 0,
22000000000144, 22000000000145, 1600, 1600, 1, 0, 0, 15
from temp_max_gid a, game_event b
where b.site_id=2 and b.game=15;

insert into pente_game
(gid, site_id, event_id, round, section, play_date, timer, rated,
 initial_time, incremental_time, player1_pid, player2_pid, player1_rating,
 player2_rating, winner, player1_type, player2_type, game)
select a.gid+1, 2, b.eid, NULL, NULL, sysdate(), 'N', 'N', 20, 0,
22000000000144, 22000000000145, 1600, 1600, 1, 0, 0, 16
from temp_max_gid a, game_event b
where b.site_id=2 and b.game=16;

insert into dsg_server_game 
select 1, b.eid, 15 
from game_event b 
where b.game=15 and b.site_id=2;

insert into dsg_server_game 
select 1, b.eid, 16
from game_event b 
where b.game=16 and b.site_id=2;

drop table temp_max_gid;