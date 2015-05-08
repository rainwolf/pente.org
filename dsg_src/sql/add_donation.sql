alter table dsg_player
add name_color int not null;

create table dsg_donation(
pid bigint unsigned not null,
primary key(pid),
amount decimal(5, 2) not null,
date datetime not null
);

insert into dsg_donation
(pid, amount, date)
values(22000000000539, 8.00, '2002-02-28 04:48:57');

insert into dsg_donation
(pid, amount, date)
values(22000000001081, 25.00, '2002-02-22 15:28:09');

insert into dsg_donation
(pid, amount, date)
values(22000000000813, 10.00, '2002-02-18 05:46:39');

insert into dsg_donation
(pid, amount, date)
values(22000000001509, 25.00, '2002-02-25 18:59:00');

insert into dsg_donation
(pid, amount, date)
values(22000000000645, 25.00, '2002-02-23 07:07:49');

update dsg_player
set name_color = -16751616
where pid in (
22000000000539,
22000000000593,
22000000001075,
22000000001084,
22000000001150,
22000000001086,
22000000004159,
22000000001081,
22000000000813,
22000000001509,
22000000000645,
22000000000890,
22000000000986,
22000000000857,
22000000001113,
22000000004050,
22000000004322
);