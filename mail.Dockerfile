FROM debian:bookworm-slim

RUN apt update && apt install postfix opendkim procps inetutils-telnet inetutils-syslogd -y && apt autoremove -y && apt autopurge -y

COPY dockerMail/ /etc

COPY dockerMail/root.key /usr/share/dns/root.key

RUN chown -R opendkim:opendkim /etc/opendkim

COPY dockerMail/start_dsg_mail.sh /start_dsg_mail.sh

ENTRYPOINT ["sh", "/start_dsg_mail.sh"]
