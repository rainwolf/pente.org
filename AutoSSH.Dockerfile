FROM alpine:latest

RUN apk update && apk add openssh autossh

COPY auto_ssh.sh /auto_ssh.sh

ENTRYPOINT ["sh", "/auto_ssh.sh"]
