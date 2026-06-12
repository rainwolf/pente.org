create table if not exists dsg_tournament (
pid bigint unsigned not null,
event_id int unsigned not null,
primary key(pid, event_id),
signup_date datetime not null
);

alter table game_event
add mailing_list varchar(30) null;

insert into game_event
(name, site_id, mailing_list)
values('Tournament 4A', 2, 'tournament4APlayers');

insert into game_event
(name, site_id, mailing_list)
values('Tournament 4B', 2, 'tournament4BPlayers');