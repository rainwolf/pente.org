alter table dsg_player
add email_visible char(1) not null,
add location varchar(100),
add sex char(1) not null,
add age tinyint unsigned not null,
add homepage varchar(100);

update dsg_player
set email_visible = 'N', sex = 'U', age = 0;