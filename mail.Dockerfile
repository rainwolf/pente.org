FROM debian:trixie-slim

RUN apt update && apt install postfix opendkim procps inetutils-telnet busybox-syslogd dns-root-data -y && apt autoremove -y && apt autopurge -y

# Configuration we own, copied to its real destination. Everything else under
# /etc/postfix and /etc/opendkim is left as the distro installed it -- notably
# master.cf, postfix-files and postfix-script, which used to be clobbered by an
# old /etc snapshot. See dockerMail/README.md.
COPY dockerMail/postfix/main.cf       /etc/postfix/main.cf
COPY dockerMail/opendkim.conf         /etc/opendkim.conf
COPY dockerMail/opendkim/KeyTable     /etc/opendkim/KeyTable
COPY dockerMail/opendkim/SigningTable /etc/opendkim/SigningTable
COPY dockerMail/opendkim/TrustedHosts /etc/opendkim/TrustedHosts
COPY dockerMail/mailname              /etc/mailname

# Secrets. These are gitignored, so a clone without them fails the build here,
# loudly, instead of silently shipping whatever was lying around.
COPY dockerMail/opendkim/keys/pente.org/mail.private /etc/opendkim/keys/pente.org/mail.private
COPY dockerMail/postfix/sasl_passwd                  /etc/postfix/sasl_passwd

# sasl_passwd.db is generated here rather than copied, so the hash can never be
# stale with respect to its source file.
RUN chmod 600 /etc/postfix/sasl_passwd \
 && postmap /etc/postfix/sasl_passwd \
 && chmod 600 /etc/postfix/sasl_passwd.db \
 && chown -R opendkim:opendkim /etc/opendkim \
 && chmod 700 /etc/opendkim/keys /etc/opendkim/keys/pente.org \
 && chmod 600 /etc/opendkim/keys/pente.org/mail.private

COPY dockerMail/start_dsg_mail.sh /start_dsg_mail.sh

# Check the seam, not just the process: postfix must answer SMTP with a 220
# banner, and the opendkim milter must be alive (an unsigned-mail outage is
# invisible to a process check on postfix alone).
HEALTHCHECK --interval=333s --timeout=10s --start-period=30s --retries=3 \
  CMD banner=$( { sleep 1; printf 'QUIT\r\n'; } | busybox nc 127.0.0.1 25 ) \
      && echo "$banner" | grep -q '^220 .*ESMTP' \
      && pgrep -x opendkim > /dev/null || exit 1

ENTRYPOINT ["sh", "/start_dsg_mail.sh"]
