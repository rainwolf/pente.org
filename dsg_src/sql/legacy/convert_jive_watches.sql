insert into jivewatch
select pid, forumID, 0, 1, 0 from dsg_watched_forums;

drop table dsg_watched_forums;

insert into jiveuserperm
select player.pid, 'jiveAutoWatchNewTopics', 'true'
from player where site_id = 2;

insert into jiveuserperm
select player.pid, 'jiveAutoWatchReplies', 'true'
from player where site_id = 2;

insert into jiveuserperm
select player.pid, 'jiveAutoAddEmailWatch', 'true'
from player where site_id = 2;