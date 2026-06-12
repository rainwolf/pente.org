create table dsg_player_prefs(
pid bigint unsigned not null,
pref_name varchar(25) not null,
primary key(pid, pref_name),
pref_value blob not null,
last_update_date datetime not null
); 