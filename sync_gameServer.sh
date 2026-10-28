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

echo "Building the pentedb app"
cd ../react_pentedb || exit
cp ../react_mmai/MMAIWASM/ai.js ../react_mmai/MMAIWASM/ai.wasm public/ai/
npm run build || exit 1
cd ../pente.org || exit

rsync -vurtz --checksum --stats --progress ../react_live_game_room/build/ debian@pente.org:~/dockerMain/gameServer/live/
rsync -vurtz --checksum --stats --progress ../react_mmai/build/ debian@pente.org:~/dockerMain/gameServer/mmai/
rsync -vurtz --checksum --stats --progress ../react_pentedb/build/ debian@pente.org:~/dockerMain/gameServer/db/

./justCompile || exit 1

set -l rsync_log (mktemp)
rsync -vurtz --checksum --stats --progress --log-file=$rsync_log --log-file-format='%i %n' deployClasses/org/ debian@pente.org:~/dockerMain/orgClasses/ || begin; rm -f $rsync_log; exit 1; end
set -l synced_count (grep -cE ' [<>]f' $rsync_log)
rm -f $rsync_log

if test "$synced_count" -eq 0
    echo "No classes changed, skipping restart of pente.org"
else if test -n "$SKIP_PROD_RESTART"
    echo "Skipping restart of pente.org, caller will handle it"
else
    echo "Restarting pente.org"
    ssh debian@pente.org docker compose -f docker-compose.yml restart pente.org
end
