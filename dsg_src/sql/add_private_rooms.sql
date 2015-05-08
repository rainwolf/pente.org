alter table dsg_player
add email_updates enum('Y', 'N') not null,
add hash_code varchar(16) not null,
add last_update_date datetime not null;

update dsg_player
set hash_code = Password(pid + password), 
status = 'A',
email_updates = 'Y',
last_update_date = sysdate();

create table dsg_return_email (
message_id varchar(100) not null,
primary key(message_id),
pid bigint unsigned not null,
email varchar(100) not null,
send_date datetime not null
);