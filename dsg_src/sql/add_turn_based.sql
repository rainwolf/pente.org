create table tb_game(
gid bigint unsigned not null auto_increment primary key,
state char(1) not null default 'N',
p1_pid bigint unsigned,
p2_pid bigint unsigned,
creation_date datetime,
start_date datetime,
last_move_date datetime,
timeout_date datetime,
completion_date datetime,
game smallint unsigned not null,
event_id int unsigned not null,
round tinyint unsigned,
section tinyint unsigned,
days_per_move tinyint unsigned not null,
rated enum('Y', 'N') not null default 'Y',
inviter_pid bigint unsigned not null
);

create table tb_move(
gid bigint unsigned not null,
move_num smallint unsigned not null,
primary key(gid, move_num),
move smallint unsigned not null
);

create table tb_message(
gid bigint unsigned not null,
seq_nbr smallint unsigned not null,
primary key(gid, seq_nbr),
move_num smallint unsigned,
message varchar(255),
date datetime not null
);

insert into tb_game
(gid, state, game, event_id, days_per_move, rated, inviter_pid)
values(50000000000000, 'C', 1, 0, 0, 'N', 0);

update game_event
set name='Live Game'
where site_id=2
and name='Non-Tournament Game';

insert into game_event
(name, site_id, game)
values
('Turn-based Game', 2, 51),
('Turn-based Game', 2, 53),
('Turn-based Game', 2, 55),
('Turn-based Game', 2, 57),
('Turn-based Game', 2, 59),
('Turn-based Game', 2, 61);
