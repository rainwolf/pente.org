#!/usr/bin/env fish

clear; printf '\e[3J';

rsync -vurtz --checksum --stats --progress dsg_src/httpdocs/gameServer/ debian@pente.org:~/dockerMain/gameServer/

echo "Building the live game room"
cd ../react_live_game_room || exit
npm run build || exit 1
echo "Building the webassembly AI"
cd ../react_mmai/MMAIWASM || exit
sh compile.sh || exit 1
cd ../
cp MMAIWASM/ai.* public/
echo "Building the AI frontend"
npm run build || exit 1
cd ../pente.org || exit

rsync -vurtz --checksum --stats --progress ../react_live_game_room/build/ debian@pente.org:~/dockerMain/gameServer/live/
rsync -vurtz --checksum --stats --progress ../react_mmai/build/ debian@pente.org:~/dockerMain/gameServer/mmai/

./justCompile

rsync -vurtz --checksum --stats --progress deployClasses/org/ debian@pente.org:~/dockerMain/orgClasses/
