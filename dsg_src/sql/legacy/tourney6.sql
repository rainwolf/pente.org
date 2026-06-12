insert into game_event
(name, site_id, game)
values('Pente - March 2005 Open', 2, 1);

insert into dsg_tournament_detail
(event_id, status, timer, initial_time, incremental_time,
 round_length_days, creation_date, signup_end_date, start_date)
values((select max(eid) from game_event), 'N', 'I', 20, 3, 14
sysdate(), '2005-03-03 23:59:59', '2005-03-04');