create table if not exists dsg_watched_forums (
pid bigint unsigned not null,
forumID bigint not null,
primary key(pid, forumID)
);