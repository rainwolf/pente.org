create table dsg_ip (
pid bigint unsigned not null,
ip varchar(15) not null,
access_time datetime not null,
ban enum('Y','N') not null default 'N',
index(pid, ban),
index(ip, ban)
);