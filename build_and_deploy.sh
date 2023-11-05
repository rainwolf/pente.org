#!/usr/bin/env bash

docker system prune -af
DOCKER_DEFAULT_PLATFORM=linux/amd64 docker compose build

docker save auto_ssh | bzip2 | pv | ssh debian@51.79.159.111 docker load && docker image prune -f
docker save dsg_sql | bzip2 | pv | ssh debian@51.79.159.111 docker load && docker image prune -f

docker save pente.org | bzip2 | pv | ssh rainwolf@pente.org docker load && docker image prune -f
docker save auto_ssh | bzip2 | pv | ssh rainwolf@pente.org docker load && docker image prune -f
docker save dsg_sql | bzip2 | pv | ssh rainwolf@pente.org docker load && docker image prune -f
docker save dsg_mail | bzip2 | pv | ssh rainwolf@pente.org docker load && docker image prune -f

docker save pente.org | bzip2 | pv | ssh rainwolf@development.pente.org docker load && docker image prune -f
docker save auto_ssh | bzip2 | pv | ssh rainwolf@development.pente.org docker load && docker image prune -f
docker save dsg_sql | bzip2 | pv | ssh rainwolf@development.pente.org docker load && docker image prune -f
docker save dsg_mail | bzip2 | pv | ssh rainwolf@development.pente.org docker load && docker image prune -f

docker system prune -af
docker compose build
