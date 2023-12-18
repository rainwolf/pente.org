#!/usr/bin/env bash

clear; printf '\e[3J';

export DOCKER_DEFAULT_PLATFORM=linux/amd64

rsync -vurtd --exclude-from exclude_compile.txt --stats --progress dsg_src/java/ deploy/

read -a array <<< "$@"

docker system prune -af

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

read -a images_main <<< $(docker compose -f docker-compose.yml config --images | sort)
read -a images_replica <<< $(docker compose -f docker-compose-replica.yml config --images | sort)
read -a built_images <<< $(docker images --format json | jq .Repository | sed 's/\"//g' | sort)
images_main_combined=( "${images_main[@]} ${built_images[@]}" )
images_main_push=$(echo "${images_main_combined[@]}" | xargs -n1 | sort | uniq -d | xargs)
images_replica_combined=( "${images_replica[@]} ${built_images[@]}" )
images_replica_push=$(echo "${images_replica_combined[@]}" | xargs -n1 | sort | uniq -d | xargs)

target="debian@pente.org"
for image in ${images_main_push[@]}
do
  echo "Pushing ${image} to ${target}"
  docker save "${image}" | bzip2 | pv | ssh "${target}" docker load
done
target="debian@wire.submanifold.be"
for image in ${images_replica_push[@]}
do
  echo "Pushing ${image} to ${target}"
  docker save "${image}" | bzip2 | pv | ssh "${target}" docker load
done


# restart the containers with new images
if [[ ${#images_main_push[@]} -ne 0 ]]
then
  echo "Restarting pente.org"
  ssh debian@pente.org docker compose -f docker-compose.yml up -d
fi
if [[ ${#images_replica_push[@]} -ne 0 ]]
then
  echo "Restarting replica"
  ssh debian@wire.submanifold.be docker compose -f docker-compose-replica.yml up -d
fi
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
