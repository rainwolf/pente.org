alter table dsg_player
add timezone varchar(100) default 'America/New_York';

update dsg_player d, jiveUserProp p
set d.timezone=p.propValue
where d.pid=p.userID
and p.name='jiveTimeZoneID';

delete from jiveUserProp
where name='jiveTimeZoneID';