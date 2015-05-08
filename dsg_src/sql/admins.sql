alter table dsg_player
add admin enum('Y','N') not null default 'N';

update dsg_player
set admin='Y'
where pid in (
    select pid from player where name in ('dweebo', 'mmammel')
);