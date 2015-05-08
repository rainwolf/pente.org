/* move to admin database */
use mysql;

/* create the dsg database */
create database if not exists dsg;

/* add 3 users that can access the dsg database */
/* all 3 can only access mysql from localhost */
/* dsg_ro has read only access to the dsg database */
/* this account is used by the game database server */
grant select on dsg.*
to dsg_ro@localhost
identified by 'dsg_ro';

/* dsg_rw has read/write access to the dsg database */
/* this account it used by the iyt_server, iyt_spider programs. */
grant select, insert, update, delete on dsg.*
to dsg_rw@localhost
identified by 'dsg_rw';

/* dsg_adm is the administrator account, it has full privileges on the dsg db */
/* dont use this from any programs.  use it only when you need to log 
/* into mysql to make changes to the database */
grant all privileges on dsg.*
to dsg_adm@localhost
identified by 'dsg_adm'
with grant option;

/* make the new accounts active */
flush privileges;

/* switch to the dsg database */
use dsg;

/* create the player table */
create table if not exists player (
pid bigint unsigned not null,
primary key(pid),
name varchar(100) not null,
index(name),
site_id smallint unsigned not null
);

/* create the game_site table */
create table if not exists game_site (
sid smallint unsigned not null auto_increment,
name varchar(100) not null,
short_name varchar(10) not null,
URL varchar(100) not null,
primary key(sid),
unique(name),
unique(short_name)
);

/* create the game_event table */
create table if not exists game_event (
eid int unsigned not null auto_increment,
name varchar(100) not null,
site_id smallint unsigned not null,
mailing_list varchar(30) null,
game tinyint unsigned not null,
primary key(eid, name)
);

/* create the pente_game table */
create table if not exists pente_game ( 
gid bigint unsigned not null,
primary key(gid),
game tinyint unsigned not null,
index(game),
site_id smallint unsigned not null,
index(site_id),
event_id int unsigned not null,
index(event_id),
round varchar(100) null,
index(round),
section varchar(100) null,
index(section),
play_date datetime not null,
index(play_date),
timer enum('N', 'S', 'I') not null,
rated enum('Y', 'N') not null,
initial_time smallint unsigned,
incremental_time smallint unsigned,
player1_pid bigint unsigned not null,
index(player1_pid),
player2_pid bigint unsigned not null,
index(player2_pid),
player1_rating smallint unsigned,
player2_rating smallint unsigned,
winner tinyint unsigned not null,
index(winner)
swapped enum('Y', 'N') not null default 'N'
);

/* create the pente_move table */
create table if not exists pente_move (
gid bigint unsigned not null,
move_num smallint unsigned not null,
position smallint unsigned not null,
hash_key int unsigned not null,
index(hash_key),
rotation tinyint unsigned not null,
primary key(gid, move_num)
);

/* create the dsg_player table */
create table if not exists dsg_player (
pid bigint unsigned not null,
primary key(pid),
password varchar(32) not null,
email varchar(100) not null,
email_valid char(1) not null,
num_logins int unsigned not null,
last_login_date datetime not null,
register_date datetime not null,
de_register_date datetime,
status char(1) not null,
email_visible char(1) not null,
location varchar(100),
sex char(1) not null,
age tinyint unsigned not null,
homepage varchar(100),
name_color int not null,
note varchar(100),
hash_code varchar(16) not null,
last_update_date datetime not null
);

/* create the dsg_player_game table */
create table if not exists dsg_player_game (
pid bigint unsigned not null,
game tinyint unsigned not null,
primary key(pid, game),
wins int unsigned not null,
losses int unsigned not null,
draws int unsigned not null,
rating decimal(14, 9) not null,
streak smallint not null,
last_game_date datetime
);

/* create table to track donations to DSG */
create table if not exists dsg_donation(
pid bigint unsigned not null,
amount decimal(5, 2) not null,
date datetime not null,
payment_method char(1)
);

/* create table to aid in tracking valid email accounts */
create table if not exists dsg_return_email (
message_id varchar(100) not null,
primary key(message_id),
pid bigint unsigned not null,
email varchar(100) not null,
send_date datetime not null
);

/* create table to track players in a tournament */
create table if not exists dsg_tournament (
pid bigint unsigned not null,
event_id int unsigned not null,
primary key(pid, event_id),
signup_date datetime not null,
rating int unsigned not null,
seed tinyint unsigned,
dropout_round tinyint unsigned not null
);

/* create table to track results of tournament games */
create table if not exists dsg_tournament_results(
result_id bigint unsigned not null auto_increment primary key,
pid1 bigint unsigned not null,
pid2 bigint unsigned not null,
event_id int unsigned not null,
round tinyint unsigned not null,
section tinyint unsigned not null,
result enum('0', '1', '2') not null default '0',
forfeit enum('Y', 'N') not null default 'N',
p1_wins tinyint not null,
p1_losses tinyint not null,
p2_wins tinyint not null,
p2_losses tinyint not null
);

/* create table for player avatars */
create table if not exists dsg_player_avatar(
pid bigint unsigned not null primary key,
avatar blob not null,
content_type varchar(100) not null
);