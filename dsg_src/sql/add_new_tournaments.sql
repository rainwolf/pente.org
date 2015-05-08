create table dsg_tournament_detail(
event_id int not null primary key,
status enum('N','S','A','C') not null default 'N',
timer enum('N', 'S', 'I') not null,
initial_time smallint unsigned,
incremental_time smallint unsigned,
round_length_days smallint unsigned,
creation_date datetime not null,
signup_end_date datetime not null,
start_date datetime not null,
completion_date datetime
);

create table dsg_tournament_player(
event_id int not null,
pid bigint unsigned not null,
primary key(event_id, pid),
signup_date datetime not null,
seed int unsigned
);

create table dsg_tournament_match(
mid bigint unsigned not null auto_increment primary key,
event_id int not null,
round tinyint not null,
section tinyint not null,
gid bigint unsigned,
p1_pid bigint unsigned not null,
p2_pid bigint unsigned not null,
result enum ('1', '2')
);