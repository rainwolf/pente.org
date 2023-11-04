FROM debian:bookworm-slim

RUN apt update && apt install postfix opendkim -y
RUN apt install procps inetutils-telnet inetutils-syslogd -y

COPY dockerMail/ /etc

COPY dockerMail/root.key /usr/share/dns/root.key

RUN chown -R opendkim:opendkim /etc/opendkim

COPY dockerMail/start_dsg_mail.sh /start_dsg_mail.sh

ENTRYPOINT ["sh", "/start_dsg_mail.sh"]
