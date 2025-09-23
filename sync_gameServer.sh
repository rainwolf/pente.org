#!/usr/bin/env fish

clear; printf '\e[3J';

rsync -vurtz --checksum --stats --progress dsg_src/httpdocs/gameServer/ debian@pente.org:~/dockerMain/gameServer/

echo "Building the live game room"
cd ../react_live_game_room || exit
npm run build || exit 1
rm -rf ../pente.org/react-live-game-room/build/*
cp -r build/* ../pente.org/react-live-game-room/build/
echo "Building the webassembly AI"
cd ../react_mmai/MMAIWASM || exit
sh compile.sh || exit 1
cd ../
cp MMAIWASM/ai.* public/
echo "Building the AI frontend"
npm run build || exit 1
rm -rf ../pente.org/react-mmai/build/*
cp -r build/* ../pente.org/react-mmai/build/
cd ../pente.org || exit

rsync -vurtz --checksum --stats --progress ./react-live-game-room/build/ debian@pente.org:~/dockerMain/gameServer/live/
rsync -vurtz --checksum --stats --progress ./react-mmai/build/ debian@pente.org:~/dockerMain/gameServer/mmai/

./justCompile

rsync -vurtz --checksum --stats --progress deployClasses/org/ debian@pente.org:~/dockerMain/orgClasses/
