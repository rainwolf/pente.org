#!/usr/bin/env bash

read -a array <<< "$@"

docker system prune -af

if [[ ${#array[@]} -eq 0 ]]
then
  echo "Building everything for linux/amd64"
  DOCKER_DEFAULT_PLATFORM=linux/amd64 docker compose build
else
  for target in "${array[@]}"
  do
    if [[ ${target} == "pente.org" ]]
    then
      echo "Building the live game room"
      cd ../react-live-game-room
      npm run build || exit 1
      echo "Building the webassembly AI"
      cd ../react_mmai/MMAIWASM
      sh compile.sh || exit 1
      cd ../
      cp MMAIWASM/ai.* public/
      echo "Building the AI frontend"
      npm run build || exit 1
      cd ../pente.org
    fi
    echo "Building ${target} for linux/amd64"
    DOCKER_DEFAULT_PLATFORM=linux/amd64 docker compose build "${target}"
  done
fi

if [[ ${#array[@]} -eq 0 || ${array[@]} =~ "main_auto_ssh" ]]
then
  for target in "debian@51.79.69.199" "debian@51.79.159.111" "rainwolf@development.pente.org"
  do
    echo "Pushing auto_ssh to ${target}"
    docker save auto_ssh | bzip2 | pv | ssh "${target}" docker load
  done
fi

if [[ ${#array[@]} -eq 0 || ${array[@]} =~ "main_db" ]]
then
  for target in "debian@51.79.69.199" "debian@51.79.159.111" "rainwolf@development.pente.org"
  do
    echo "Pushing dsg_sql to ${target}"
    docker save dsg_sql | bzip2 | pv | ssh "${target}" docker load
  done
fi

if [[ ${#array[@]} -eq 0 || ${array[@]} =~ "pente_mail" ]]
then
  for target in "debian@51.79.69.199" "rainwolf@development.pente.org"
  do
    echo "Pushing dsg_mail to ${target}"
    docker save dsg_mail | bzip2 | pv | ssh "${target}" docker load
  done
fi

if [[ ${#array[@]} -eq 0 || ${array[@]} =~ "pente.org" ]]
then
  for target in "debian@51.79.69.199" "rainwolf@development.pente.org"
  do
    echo "Pushing pente.org to ${target}"
    docker save pente.org | bzip2 | pv | ssh "${target}" docker load
  done
fi

for target in "debian@51.79.69.199" "debian@51.79.159.111" "rainwolf@development.pente.org" "rainwolf@pente.org"
do
  echo "Cleaning up ${target}"
  ssh "${target}" docker image prune -f
done

docker system prune -af
docker compose build

docker builder prune -af

docker image ls
