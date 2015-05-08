/* see tracker bug #444785, and tracker task #444788 */
/* this makes the name column case-sensitive */
/* for sorting/searching and updates */
/* the games that were stored incorrectly */

alter table player
modify name varchar(100) binary not null;

update pente_game
set player1_pid = 15200000428961
where gid in (
15300003528495,
15300003528501,
15300003528505,
15300003866441,
15300003866442,
15300003866435);

update pente_game
set player2_pid = 15200000428961
where gid in (
15300003528494,
15300003528500,
15300003528504,
15300003866440,
15300003866443,
15300003866434);