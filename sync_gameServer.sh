#!/usr/bin/env fish

clear; printf '\e[3J';

rsync -vurtz --checksum --stats --progress dsg_src/httpdocs/gameServer/ debian@pente.org:~/dockerMain/gameServer/

rsync -vurtz --checksum --stats --progress ./react-live-game-room/build/ debian@pente.org:~/dockerMain/gameServer/live/
rsync -vurtz --checksum --stats --progress ./react-mmai/build/ debian@pente.org:~/dockerMain/gameServer/mmai/

./justCompile

rsync -vurtz --checksum --stats --progress deployClasses/org/ debian@pente.org:~/dockerMain/orgClasses/
