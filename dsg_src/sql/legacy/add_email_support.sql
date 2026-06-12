create table dsg_server_access (
server_id int unsigned not null,
pid bigint unsigned not null,
primary key(server_id, pid)
);

alter table dsg_server
add private enum('Y','N') not null default 'N';