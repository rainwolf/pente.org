alter table pente_game
add private enum('Y','N') default 'N' not null;

alter table tb_set
add private enum('Y','N') default 'N' not null;