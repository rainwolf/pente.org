alter table dsg_player_game
modify tourney_winner enum('0','1','2','3') default '0';

update dsg_player_game set tourney_winner='1' where pid=23000000001275 
and computer='N' and game=1;

update dsg_player_game set tourney_winner='1' where pid=23000000003311 
and computer='N' and game=2;

update dsg_player_game set tourney_winner='2' where pid=23000000001931 
and computer='N' and game=1;