#old game #'s
#PENTE = 0;
#KERYO = 1;
#GOMOKU = 2;
#GPENTE = 3;
#POOF_PENTE = 4;
#DPENTE = 5;

#new game #'s
#PENTE 1
#SPEED PENTE 2
#KERYO 3
#SPEED KERYO 4
#GOMOKU 5
#SPEED GOMOKU 6
#D-PENTE 7
#SPEED D-PENTE 8
#G-PENTE 9
#SPEED G-PENTE 10
#POOF PENTE 11
#SPEED POOF PENTE 12

# conversion
# game = (game - 1)/2;

select concat('Starting speed conversion at ', sysdate()) as ' ';

select 'Updating dsg_player_game game numbers' as ' ';

# move around game numbers
update dsg_player_game
set game = 11 where game = 4;
update dsg_player_game
set game = 9 where game = 3;
update dsg_player_game
set game = 7 where game = 5;
update dsg_player_game
set game = 5 where game = 2;
update dsg_player_game
set game = 3 where game = 1;
update dsg_player_game
set game = 1 where game = 0;


select 'Updating pente_game game numbers' as ' ';
update pente_game
set game = 11 where game = 4;
update pente_game
set game = 9 where game = 3;
update pente_game
set game = 7 where game = 5;
update pente_game
set game = 5 where game = 2;
update pente_game
set game = 3 where game = 1;
update pente_game
set game = 1 where game = 0;


select 'Updating game_event game numbers' as ' ';
update game_event
set game = 11 where game = 4;
update game_event
set game = 9 where game = 3;
update game_event
set game = 7 where game = 5;
update game_event
set game = 5 where game = 2;
update game_event
set game = 3 where game = 1;
update game_event
set game = 1 where game = 0;


select 'Updating pente_game, mark speed games' as ' ';
# mark speed games in pente_game
update pente_game
set game = game + 1
where site_id = 2
and player1_type = '0'
and player2_type = '0'
and timer in ('S','I')
and (initial_time * 60 + incremental_time * 15 < 331);

select 'Inserting new game_events' as ' ';
# insert new game_events for new games
insert into game_event
(name, site_id, game)
values('Non-Tournament Game', 2, 2);
insert into game_event
(name, site_id, game)
values('Non-Tournament Game', 2, 4);
insert into game_event
(name, site_id, game)
values('Non-Tournament Game', 2, 6);
insert into game_event
(name, site_id, game)
values('Non-Tournament Game', 2, 8);
insert into game_event
(name, site_id, game)
values('Non-Tournament Game', 2, 10);
insert into game_event
(name, site_id, game)
values('Non-Tournament Game', 2, 12);

select 'Updating pente_game with correct game events' as ' ';
# make sure not to include tournament games
update pente_game a, game_event b, game_event c
set a.event_id = b.eid
where a.game = b.game
and a.game % 2 = 0
and a.site_id = 2
and b.site_id = 2
and b.name = 'Non-Tournament Game'
and a.event_id = c.eid
and c.name = 'Non-Tournament Game';


select 'De-registering speed accounts in dsg_player' as ' ';
# de-register speed accounts
update dsg_player p, speed_mapping s
set p.status = 'S', p.de_register_date=sysdate()
where p.pid = s.speed_pid;
# part of de-registering is removing avatars
delete from dsg_player_avatar a
using dsg_player_avatar a, speed_mapping s
where a.pid = s.speed_pid;

select 'Copying over speed stats to normal account' as ' ';
# copy over game stats from speed account to normal account
insert into dsg_player_game
select s.normal_pid, g.game + 1, g.wins, g.losses, g.draws, g.rating, g.streak, g.last_game_date, g.computer
from speed_mapping s, dsg_player_game g
where s.speed_pid = g.pid
and g.computer = 'N';

# leave speed account stats there, just in case
select 'Deleting speed player stats' as ' ';
# delete speed account game stats
delete from dsg_player_game g
using dsg_player_game g, speed_mapping s
where g.pid = s.speed_pid;

select 'Moving games over from speed players' as ' ';
# update pente_game pid to the normal pid, from the speed pid
update pente_game g, speed_mapping s
set g.player1_pid = s.normal_pid
where g.player1_pid = s.speed_pid;
update pente_game g, speed_mapping s
set g.player2_pid = s.normal_pid
where g.player2_pid = s.speed_pid;

select 'Moving forum messages over from speed players' as ' ';
# update forum tables to switch any posts made by speed player
# to normal player
update jiveMessage m, speed_mapping s
set m.userID = s.normal_pid
where m.userID = s.speed_pid;

select 'Deleting forum watches held by speed players' as ' ';
# delete any watches from speed players
delete from jiveWatch j
using jiveWatch j, speed_mapping s
where j.userID = s.speed_pid;

select concat('Finished speed conversion at ', sysdate()) as ' ';

# don't worry about dsg_ip, dsg_return_email data, 
# dsg_player_prefs, dsg_donation
# dsg_tournament, dsg_tournament_results shouldn't have any speed id's anyways
# for speed player, not a big deal