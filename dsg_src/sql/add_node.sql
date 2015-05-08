create table node (
hash_key bigint not null primary key,
parent_key bigint not null,
player tinyint unsigned not null,
position smallint unsigned not null,
rotation tinyint unsigned not null,
depth smallint unsigned not null,
type tinyint unsigned not null,
score smallint unsigned not null,
comment varchar(1000)
);

create table node_next (
hash_key bigint not null,
next_key bigint not null,
primary key(hash_key, next_key)
);

insert into node
(hash_key, parent_key, player, position, rotation, depth, type, score)
values(0, 0, 0, 0, 0, 0, 5, 0);
insert into node
(hash_key, parent_key, player, position, rotation, depth, type, score)
values(-4230528974382094949, 0, 1, 180, 0, 1, 5, 0);

insert into node_next
values(0, -4230528974382094949);