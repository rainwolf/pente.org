create table tb_vacation(
pid bigint unsigned not null,
date date not null,
primary key(pid, date)
);