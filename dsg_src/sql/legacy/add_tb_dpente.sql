alter table tb_game
add dpente_state tinyint unsigned,
add dpente_swap enum('Y','N');

alter table tb_message
add pid bigint unsigned not null,
drop primary key;

update tb_message m, tb_game g
set m.pid=g.inviter_pid
where m.gid=g.gid
and m.move_num=0 and m.seq_nbr=0;

update tb_message m, tb_game g
set m.pid=g.inviter_pid
where m.gid=g.gid
and g.inviter_pid=g.p2_pid
and m.move_num=0 and m.seq_nbr=1;

update tb_message m, tb_game g
set m.pid=g.p2_pid
where m.gid=g.gid
and g.inviter_pid=g.p1_pid
and m.move_num=0 and m.seq_nbr=1;

update tb_message m, tb_game g
set m.pid=g.p1_pid
where m.gid=g.gid
and m.move_num%2=1;

update tb_message m, tb_game g
set m.pid=g.p2_pid
where m.gid=g.gid
and m.move_num>0 && m.move_num%2=0;

alter table tb_message
add primary key(gid, move_num, pid);