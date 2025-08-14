#!/bin/sh

mkdir -p /var/spool/postfix/etc/
cp /etc/resolv.conf /var/spool/postfix/etc/resolv.conf
syslogd
opendkim -A
postfix start
tail -f /var/log/mail.log
