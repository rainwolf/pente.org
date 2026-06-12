insert into game_site
values(4, 'BrainKing', 'BK', 'http://www.brainking.com');

insert into game_event
(eid, site_id, name, game)
values(1000, 4, 'temp', 1);

insert into player
values(40000000000000,'temp',4);

insert into pente_game
(gid,site_id,event_id,play_date,timer,rated,player1_pid,player2_pid,
winner,player1_type,player2_type,game,swapped)
values(41000000000000,4,1000,sysdate(),'N','N',0,0,1,0,0,1,'N');