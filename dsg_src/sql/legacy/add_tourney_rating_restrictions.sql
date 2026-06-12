alter table dsg_tournament_detail
add rating_restriction_type enum('0','1','2') not null default '0',
add rating_restriction smallint unsigned,
add prize varchar(32);