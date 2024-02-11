#!/usr/bin/env bash

clear; printf '\e[3J';

[ ! -f .env ] || export $(grep -v '^#' .env | xargs)

openssl req -x509 -out localhost.crt -keyout localhost.key \
  -newkey rsa:2048 -nodes -sha256 \
  -subj '/CN=localhost' -extensions EXT -config <( \
   printf "[dn]\nCN=localhost\n[req]\ndistinguished_name = dn\n[EXT]\nsubjectAltName=DNS:localhost\nkeyUsage=digitalSignature\nextendedKeyUsage=serverAuth")

openssl pkcs12 -export -in localhost.crt -inkey localhost.key -out fullchain_and_key.p12 -password pass:"$SSL_PWD" -name tomcat
keytool -importkeystore -deststorepass "$SSL_PWD" -destkeypass "$SSL_PWD" -destkeystore MyDSKeyStore.jks -srckeystore fullchain_and_key.p12 -srcstoretype PKCS12 -srcstorepass "$SSL_PWD" -alias tomcat -deststoretype pkcs12

mv MyDSKeyStore.jks ./dockerMain/MyDSKeyStore.jks
rm localhost.crt
rm localhost.key
