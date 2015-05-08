create table dsg_player_ignore(
ignore_id bigint unsigned primary key auto_increment,
pid bigint unsigned not null,
ignore_pid bigint not null,
ignore_invite varchar(1) not null,
ignore_chat varchar(1) not null,
last_update_date datetime not null
); 