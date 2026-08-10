#!/bin/sh

set -e

# Trusted / relaying subnet for the Docker network this container runs on.
# Rendered into BOTH postfix's mynetworks and opendkim's TrustedHosts from the
# @MAIL_DOCKER_SUBNET@ placeholder, so the two can never drift apart again.
# Override by setting MAIL_DOCKER_SUBNET on the container (see dockerMail/README.md).
MAIL_DOCKER_SUBNET="${MAIL_DOCKER_SUBNET:-172.0.0.0/8}"
echo "start_dsg_mail: trusted docker subnet = ${MAIL_DOCKER_SUBNET}"

# Validate before substituting. Everything below this point ends in `tail -f`,
# which would happily keep the container "running" with a dead mail system, so
# a bad render has to be fatal here rather than silent later.
if ! echo "${MAIL_DOCKER_SUBNET}" | grep -Eq '^[0-9]{1,3}(\.[0-9]{1,3}){3}/[0-9]{1,2}$'; then
    echo "start_dsg_mail: FATAL: MAIL_DOCKER_SUBNET='${MAIL_DOCKER_SUBNET}' is not an IPv4 CIDR" >&2
    exit 1
fi

sed -i "s|@MAIL_DOCKER_SUBNET@|${MAIL_DOCKER_SUBNET}|g" \
    /etc/postfix/main.cf \
    /etc/opendkim/TrustedHosts

# A surviving placeholder means the render did not take: refuse to start.
if grep -l '@MAIL_DOCKER_SUBNET@' /etc/postfix/main.cf /etc/opendkim/TrustedHosts; then
    echo "start_dsg_mail: FATAL: @MAIL_DOCKER_SUBNET@ survived rendering in the files above" >&2
    exit 1
fi

mkdir -p /var/spool/postfix/etc/
cp /etc/resolv.conf /var/spool/postfix/etc/resolv.conf

# Catch a config postfix would choke on before we background anything.
postfix check || exit 1

syslogd
opendkim -A
postfix start
tail -f /var/log/mail.log
