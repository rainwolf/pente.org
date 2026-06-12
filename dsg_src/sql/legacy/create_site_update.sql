/* create the site_update table */
create table if not exists site_update (
uid int unsigned not null auto_increment,
primary key(uid),
update_date datetime not null,
index(update_date),
update_msg text not null
);

insert into site_update
(update_date, update_msg)
values('2001-06-22 00:00:00', 'Modified the server to gzip the html output if a users browser supports it (most browsers do). This makes downloads up to 20 times quicker than before.');

insert into site_update
(update_date, update_msg)
values('2001-06-22 00:00:01', 'Fixed a bug in the hashing algorithm that was sometimes causing games to not match after a capture was made.');

insert into site_update
(update_date, update_msg)
values('2001-06-26 00:00:00', 'Implemented sorting of results table by position, games and percentages.');

insert into site_update
(update_date, update_msg)
values('2001-06-29 00:00:00', 'Fixed a bug in the sorting of results table in netscape.');

insert into site_update
(update_date, update_msg)
values('2001-06-29 00:00:01', 'Now in the &quot;Recent Games&quot; table, the winner of the game shows up in yellow. Also in the same table I added text to tell which games you\'re looking at (eg. 10-15 of 1038 matching). And the &quot;&lt;&lt;&quot; and &quot;&gt;&gt;&quot; links won\'t appear when no more games are available.');

insert into site_update
(update_date, update_msg)
values('2001-06-29 00:00:02', 'Now captured stones show up below the board so you can keep track of the number of captures.');

insert into site_update
(update_date, update_msg)
values('2001-07-02 00:00:00', 'Added a table of the overall database statistics to the top of each search page.');

insert into site_update
(update_date, update_msg)
values('2001-07-03 00:00:00', 'Setup pente.org to send all games over via http in real time.');

insert into site_update
(update_date, update_msg)
values('2001-07-09 00:00:00', 'Now when changing the number of matched games to display, the page will reload automatically without you having to search again.');

insert into site_update
(update_date, update_msg)
values('2001-07-12 00:00:00', 'Added functionality to filter games by site, event, round, and section. See the <a href="#instructions"><b>instructions</b></a> for more information on filtering.');

insert into site_update
(update_date, update_msg)
values('2001-07-12 00:00:01', 'Released the source code behind the Database as open source.');

insert into site_update
(update_date, update_msg)
values('2001-07-21 00:00:00', 'Added functionality to filter games by date.');

insert into site_update
(update_date, update_msg)
values('2001-07-21 00:00:01', 'Added recent games from 2000 PBeM Pente Tournament.');

insert into site_update
(update_date, update_msg)
values('2001-07-21 00:00:02', 'Added December 2000 IYT Pro-Pente Tournament games.');

insert into site_update
(update_date, update_msg)
values('2001-07-22 00:00:00', 'Added January 2001 Main #1 IYT Pro-Pente Tournament games.');

insert into site_update
(update_date, update_msg)
values('2001-07-22 00:00:01', 'Added functionality to filter games by winner.');

insert into site_update
(update_date, update_msg)
values('2001-07-23 00:00:00', 'Improved query performance by tweaking a few things.');

insert into site_update
(update_date, update_msg)
values('2001-07-23 00:00:01', 'Marked games played in Tournament 1 at DSG up to round 5 (they used to appear as &quot;Non-Tournament Games&quot;).');

insert into site_update
(update_date, update_msg)
values('2001-07-23 00:00:02', 'Some of you may have noticed that the database was down for almost <b>10 hours</b> today. It took me a long time to realize what had happened. I finally ended up wiping out the database completely and rebuilding it from a backup I have on my local machine. Then I had to update the last 30 or so games played at DSG since I last backed up. Hopefully I recovered everything, and I think I fixed the problem that started everything in the first place.');

insert into site_update
(update_date, update_msg)
values('2001-07-25 00:00:00', 'Added the rest of January and February 2001 IYT Pro-Pente Tournament games.');

insert into site_update
(update_date, update_msg)
values('2001-07-25 00:00:01', 'Added all the March 2001 IYT Pro-Pente Tournament games.');

insert into site_update
(update_date, update_msg)
values('2001-07-27 00:00:00', 'Sometime this evening we surpassed <font color="red"><b>1,000,000</b></font> moves in the database!');

insert into site_update
(update_date, update_msg)
values('2001-07-30 00:00:00', 'Fixed a rather important bug that was causing some move results to not be included in the num games and win % fields for certain moves.');

insert into site_update
(update_date, update_msg)
values('2001-07-30 00:00:01', 'Fixed the so called "pentemaster" bug. There are 2 players at IYT named "pentemaster", and "PenteMaster".  The database was storing games played by "pentemaster" with "PenteMaster".  There are no other pairs of players with this same problem.');

