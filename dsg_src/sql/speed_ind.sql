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