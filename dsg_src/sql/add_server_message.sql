create table dsg_server_message (
server_id int unsigned not null,
message_seq tinyint unsigned not null,
primary key(server_id, message_seq),
message varchar(255) not null
);