FROM alpine:latest

RUN apk update && apk add openssh autossh mariadb-client

COPY dockerSSH/auto_ssh.sh /auto_ssh.sh

ENTRYPOINT ["sh", "/auto_ssh.sh"]
