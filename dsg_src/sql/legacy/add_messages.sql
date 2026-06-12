create table dsg_message(
mid int unsigned not null auto_increment primary key,
from_pid bigint unsigned not null,
to_pid bigint unsigned not null,
subject varchar(255) not null,
body text not null,
creation_date datetime not null,
read_fl char(1) not null default 'N',
viewable char(1) not null default 'Y',
);