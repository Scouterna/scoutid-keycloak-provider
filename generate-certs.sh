#!/bin/env bash

# This script generates self-signed certificates for development.

mkdir -p certs

openssl req -new -newkey rsa:4096 -days 365 -nodes -x509 \
    -subj "/C=SE/ST=Nothing/L=Nowhere/O=Dis/CN=keycloak-dev" \
    -keyout certs/keycloak.key.pem  -out certs/keycloak.cert.pem

openssl req -new -newkey rsa:4096 -days 365 -nodes -x509 \
    -subj "/C=SE/ST=Nothing/L=Nowhere/O=Dis/CN=server-dev" \
    -keyout certs/server.key.pem  -out certs/server.cert.pem
