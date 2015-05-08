create cached table player (pid bigint identity, name varchar(100) not null, site_id smallint not null, name_lower varchar(100));
create cached table game_site (sid smallint primary key, name varchar(100) not null, short_name varchar(10) not null, URL varchar(100) not null);
create cached table game_event (eid int primary key, name varchar(100) not null, site_id smallint not null, mailing_list varchar(30), game tinyint not null);
create cached table pente_game_pro (gid bigint primary key, site_id smallint not null, event_id int not null, round varchar(100), section varchar(100), play_date datetime not null, timer char(1) not null, rated char(1) not null, initial_time smallint, incremental_time smallint, player1_pid bigint not null, player2_pid bigint not null, player1_rating smallint, player2_rating smallint, winner tinyint, player1_type char(1) not null, player2_type char(1) not null, game tinyint not null,swapped char(1) not null,private char(1) not null); 
create cached table pente_move_pro (gid bigint not null, move_num smallint not null, next_move smallint not null, hash_key bigint not null, rotation tinyint not null, game tinyint null, winner tinyint not null, play_date datetime not null);

alter table pente_move_pro add primary key(gid, move_num);
create index ind1 on pente_move_pro (hash_key, move_num, game, play_date);

