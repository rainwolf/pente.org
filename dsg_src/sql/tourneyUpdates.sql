alter table dsg_tournament_match
add forfeit enum ('Y','N') not null default 'N';

alter table dsg_tournament_detail
add column forumID bigint unsigned;

create table dsg_tournament_admin(
event_id int unsigned not null,
pid bigint unsigned not null
);