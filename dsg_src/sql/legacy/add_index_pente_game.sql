alter table pente_game
add index(player1_pid),
add index(player2_pid),
add index(site_id),
add index(play_date),
add index(winner);