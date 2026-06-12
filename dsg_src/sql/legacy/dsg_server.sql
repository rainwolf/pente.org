create table dsg_server(
id int unsigned not null auto_increment primary key,
name varchar(255) not null,
port int not null,
tournament enum('Y','N') not null default 'N',
active enum('Y','N') not null default 'Y',
creation_date datetime not null,
last_mod_date datetime not null
);

create table dsg_server_game(
server_id int unsigned not null,
event_id int unsigned not null,
game tinyint unsigned not null,
primary key(server_id, event_id, game)
);

insert into dsg_server
(name, port, creation_date, last_mod_date)
values('Main Room', 16000, sysdate(), sysdate());

insert into dsg_server_game
select s.id, e.eid, e.game 
from dsg_server s, game_event e
where e.site_id=2 
and e.name='Non-Tournament Game'
order by game;