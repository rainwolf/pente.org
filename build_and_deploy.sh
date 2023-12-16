#!/usr/bin/env bash

clear; printf '\e[3J';
rsync -vurtd --exclude-from exclude_compile.txt --stats --progress dsg_src/java/ deploy/

read -a array <<< "$@"

docker system prune -af

export DOCKER_DEFAULT_PLATFORM=linux/amd64

if [[ ${#array[@]} -eq 0 ]]
then
  echo "Building everything for linux/amd64"
  docker compose -f docker-compose.yml -f docker-compose-replica.yml build || exit 1
else
  for target in "${array[@]}"
  do
    if [[ ${target} == "pente.org" ]]
    then
      echo "Building the live game room"
      cd ../react-live-game-room
      npm run build || exit 1
      rm -rf ../pente.org/react-live-game-room/build/*
      cp -r build/* ../pente.org/react-live-game-room/build/
      echo "Building the webassembly AI"
      cd ../react_mmai/MMAIWASM
      sh compile.sh || exit 1
      cd ../
      cp MMAIWASM/ai.* public/
      echo "Building the AI frontend"
      npm run build || exit 1
      rm -rf ../pente.org/react-mmai/build/*
      cp -r build/* ../pente.org/react-mmai/build/
      cd ../pente.org
    fi
    echo "Building ${target} for linux/amd64"
    docker compose -f docker-compose.yml -f docker-compose-replica.yml build "${target}" || exit 1
  done
fi

if [[ ${#array[@]} -eq 0 || ${array[@]} =~ "main_auto_ssh" ]]
then
  for target in "debian@pente.org" "debian@wire.submanifold.be"
  do
    echo "Pushing auto_ssh to ${target}"
    docker save auto_ssh | bzip2 | pv | ssh "${target}" docker load
  done
fi

if [[ ${#array[@]} -eq 0 || ${array[@]} =~ "main_db" ]]
then
  for target in "debian@pente.org" "debian@wire.submanifold.be"
  do
    echo "Pushing dsg_sql to ${target}"
    docker save dsg_sql | bzip2 | pv | ssh "${target}" docker load
  done
fi

if [[ ${#array[@]} -eq 0 || ${array[@]} =~ "pentai" ]]
then
  for target in "debian@wire.submanifold.be"
  do
    echo "Pushing dsg_pentai to ${target}"
    docker save dsg_pentai | bzip2 | pv | ssh "${target}" docker load
  done
fi

if [[ ${#array[@]} -eq 0 || ${array[@]} =~ "pente_mail" ]]
then
  for target in "debian@pente.org"
  do
    echo "Pushing dsg_mail to ${target}"
    docker save dsg_mail | bzip2 | pv | ssh "${target}" docker load
  done
fi

if [[ ${#array[@]} -eq 0 || ${array[@]} =~ "pente.org" ]]
then
  for target in "debian@pente.org"
  do
    echo "Pushing pente.org to ${target}"
    docker save pente.org | bzip2 | pv | ssh "${target}" docker load
  done
fi

# restart the containers with new images
ssh debian@pente.org docker compose -f docker-compose.yml up -d
ssh debian@wire.submanifold.be docker compose -f docker-compose-replica.yml up -d
# clean up dangling images
for target in "debian@pente.org" "debian@wire.submanifold.be"
do
  echo "Cleaning up ${target}"
  ssh "${target}" docker image prune -f
done

unset DOCKER_DEFAULT_PLATFORM

docker system prune -af
docker compose -f docker-compose.yml -f docker-compose-replica.yml build

docker builder prune -af

docker image ls
