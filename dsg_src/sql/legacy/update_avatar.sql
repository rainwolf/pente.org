alter table dsg_player_avatar
add last_update_date datetime not null;

update dsg_player_avatar
set last_update_date = sysdate();
