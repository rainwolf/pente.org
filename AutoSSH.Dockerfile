FROM alpine:3.20.6

RUN apk update && apk add openssh autossh mariadb-client

COPY dockerSSH/auto_ssh.sh /auto_ssh.sh

ENTRYPOINT ["sh", "/auto_ssh.sh"]
