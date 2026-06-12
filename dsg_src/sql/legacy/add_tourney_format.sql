alter table dsg_tournament_detail
add format tinyint unsigned not null,
add speed enum('Y','N') not null default 'N';

alter table dsg_tournament_match
add match_seq tinyint unsigned not null;