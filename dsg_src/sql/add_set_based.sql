create table dsg_live_set(
sid bigint unsigned primary key auto_increment,
p1_pid bigint unsigned not null,
p2_pid bigint unsigned not null,
g1_gid bigint unsigned,
g2_gid bigint unsigned,
status char(1) not null,
winner tinyint unsigned,
creation_date datetime not null,
completion_date datetime
);

alter table pente_game
add set_id bigint unsigned,
add status char(1);

/* will that make a big size diff for existing rows? 91mb */
alter table pente_move
add seconds_left smallint unsigned;