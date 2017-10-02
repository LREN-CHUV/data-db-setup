#!/usr/bin/env bash
set -e

DOCKERIZE_OPTS=""

if [ ! -z "$@" ]; then
    if [ -z "$FLYWAY_HOST" ] || [ -z "$FLYWAY_DBMS" ]
    then
        echo "Usage: docker run $IMAGE with the following environment variables"
        echo
        echo "FLYWAY_DBMS: [required] Type of the database (oracle, postgres...)."
        echo "FLYWAY_HOST: [required] database host."
        echo "FLYWAY_PORT: database port."
        echo "FLYWAY_USER: database user."
        echo "FLYWAY_PASSWORD: database password."
        exit 1
    else
        DOCKERIZE_OPTS="-wait tcp://${FLYWAY_HOST}:${FLYWAY_PORT:-5432} -template /flyway/conf/flyway.conf.tmpl:/flyway/conf/flyway.conf"
    fi
fi

exec dockerize $DOCKERIZE_OPTS flyway $@ || {
  err=$?
  echo "Migration failed. It was using the following environment variables:"
  env | grep -v PASSWORD | grep -v PWD
  exit $err
}
