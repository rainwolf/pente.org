#!/usr/bin/env bash

clear; printf '\e[3J';

[ ! -f .env ] || export $(grep -v '^#' .env | xargs)

[ ! -f cert.p12 ] && echo "missing cert.p12" && exit 1
[ ! -f key.p12 ] && echo "missing key.p12" && exit 1

# extract p12 from keychain access (private key and cert)
openssl pkcs12 -clcerts -nokeys -out cert.pem -in cert.p12 -legacy
openssl pkcs12 -nocerts -out key.pem -in key.p12 -legacy
openssl pkcs12 -export -out PenteLiveAPNSkey.p12 -inkey key.pem -in cert.pem -pass env:SSL_PWD
openssl pkcs12 -in PenteLiveAPNSkey.p12 -out PenteLiveAPNSkey.pem -nodes -pass env:SSL_PWD

