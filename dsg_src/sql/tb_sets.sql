create table tb_set (
sid bigint unsigned not null primary key auto_increment,
gid1 bigint unsigned not null,
gid2 bigint unsigned,
p1_pid bigint unsigned,
p2_pid bigint unsigned,
state char(1) not null default 'N',
creation_date datetime,
completion_date datetime,
inviter_pid bigint unsigned not null
);

insert into tb_set
(gid1, p1_pid, p2_pid, state, creation_date, completion_date, inviter_pid)
select gid, p1_pid, p2_pid, state, creation_date, completion_date, inviter_pid
from tb_game;

update tb_game set rated='N';

update pente_game g
set rated='N', player1_rating=1600, player2_rating=1600
where g.gid in (select gid from tb_game);

delete from dsg_player_game
where game > 14;