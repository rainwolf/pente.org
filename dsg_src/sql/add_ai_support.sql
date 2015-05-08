alter table dsg_player_game
add column computer char(1) not null default 'N',
drop primary key,
add primary key(pid, game, computer);

alter table dsg_player
add column player_type char(1) not null default 'H';

alter table pente_game
add column player1_type char(1) not null default '0',
add column player2_type char(1) not null default '0';