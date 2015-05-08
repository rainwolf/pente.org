/** Copy over all players who have email_updates to Yes to watch the news forum */
insert into dsg_watched_forums
select pid, '4'
from dsg_player
where status = 'A'
and email_valid = 'Y'
and email_updates = 'Y';

alter table dsg_player
drop email_updates;