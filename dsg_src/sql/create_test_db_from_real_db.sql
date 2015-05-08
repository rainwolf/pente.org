delete from dsg_donation;

insert into dsg_donation values(22000000000002, 1.00, sysdate(), 'P');

delete from dsg_ip;

delete from dsg_player;

insert into dsg_player
values(22000000000002, 'mGOSDhbaSY4=', 'dweebo@pente.org', 'Y', 1, sysdate(), sysdate(),
NULL, 'A', 'Y', 'dev location', 'M', 25, 'http://pente.org/', -65536,
Password('mGOSDhbaSY4='), sysdate(), 'H', NULL);

create table temp_pid(
pid bigint
);

insert into temp_pid
select max(pid) + 1 from player where site_id = 2;

insert into player
select pid, 'dev', 2 from temp_pid;

insert into dsg_player
select pid, 'mGOSDhbaSY4=', 'dev@pente.org', 'Y', 1, sysdate(), sysdate(),
NULL, 'A', 'Y', 'dev location', 'M', 25, 'http://www.google.com/', 0,
Password('mGOSDhbaSY4='), sysdate(), 'H', NULL from temp_pid;


delete from dsg_player_avatar;

delete from dsg_player_game;

insert into dsg_player_game
values(22000000000002, 0, 10, 10, 0, 1580, 1, sysdate(),'N');

insert into dsg_player_game
values(22000000000002, 0, 10, 10, 0, 1580, 1, sysdate(), 'Y');

insert into dsg_player_game
values(22000000000002, 1, 10, 5, 0, 1810, 1, sysdate(), 'N');

insert into dsg_player_game
values(22000000000002, 1, 10, 5, 0, 1810, 1, sysdate(), 'Y');

insert into dsg_player_game
select pid, 0, 22, 15, 0, 1765, 1, sysdate(), 'N' from temp_pid;

insert into dsg_player_game
select pid, 0, 22, 15, 0, 1765, 1, sysdate(), 'Y' from temp_pid;

insert into dsg_player_game
select pid, 1, 234, 9, 0, 1920, 1, sysdate(), 'N' from temp_pid;

insert into dsg_player_game
select pid, 1, 234, 9, 0, 1920, 1, sysdate(), 'Y' from temp_pid;


delete from dsg_player_prefs;

delete from dsg_return_email;

delete from dsg_tournament;

delete from dsg_tournament_results;


delete from jiveAttachment;

delete from jiveAttachmentProp;

delete from jiveMessage;

delete from jiveMessageProp;

delete from jiveThread;

delete from jiveThreadProp;

delete from jiveUserProp;

insert into jiveUserProp
select dsg_player.pid, 'jiveAutoWatchNewTopics', 'true'
from dsg_player;

insert into jiveUserProp
select dsg_player.pid, 'jiveAutoWatchReplies', 'true'
from dsg_player;

insert into jiveUserProp
select dsg_player.pid, 'jiveAutoAddEmailWatch', 'true'
from dsg_player;

delete from jiveWatch;


delete from pente_game where gid > 15300001775000 and gid < 20000000000000;

delete from pente_move where gid > 15300001775000 and gid < 20000000000000;

delete from pente_game where gid > 21000000001000 and gid < 30000000000000;

delete from pente_move where gid > 21000000001000 and gid < 30000000000000;

delete from pente_game where gid > 31000000001000;

delete from pente_move where gid > 31000000001000;


drop table temp_pid;
