alter table tb_set
add cancel_pid bigint,
add cancel_msg varchar(255);

update tb_set set cancel_pid=0;