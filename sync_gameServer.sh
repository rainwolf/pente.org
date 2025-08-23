#!/usr/bin/env bash

clear; printf '\e[3J';

rsync -vurtz --checksum --stats --progress dsg_src/httpdocs/gameServer/ debian@pente.org:~/dockerMain/gameServer/
