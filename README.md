[![CHUV](https://img.shields.io/badge/CHUV-LREN-AF4C64.svg)](https://www.unil.ch/lren/en/home.html) [![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](https://github.com/LREN-CHUV/data-db-setup/blob/master/LICENSE) [![DockerHub](https://img.shields.io/badge/docker-hbpmip%2Fdata--db--setup-008bb8.svg)](https://hub.docker.com/r/hbpmip/data-db-setup/) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/fb70732c6b7647699610bdb7be1d8548)](https://www.codacy.com/app/hbpmip/data-db-setup?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=LREN-CHUV/data-db-setup&amp;utm_campaign=Badge_Grade)
[![CircleCI](https://circleci.com/gh/LREN-CHUV/data-db-setup.svg?style=svg)](https://circleci.com/gh/LREN-CHUV/data-db-setup)

# Setup for database 'data-db'

## Introduction

This project uses Flyway to manage the database migration scripts for the 'research-db' database used by MIP.

This database contains the data used for reference, including:

* the features (values) for each Common Data Elements (CDE) defined by MIP.

## Usage

Run:

```console
$ docker run -i -t --rm -e FLYWAY_HOST=`hostname` hbpmip/data-db-setup:2.0.0 migrate
```

where the environment variables are:

* FLYWAY_HOST: database host, default to 'db'.
* FLYWAY_PORT: database port, default to 5432.
* FLYWAY_DATABASE_NAME: name of the database or schema, default to 'data'
* FLYWAY_URL: JDBC url to the database, constructed by default from FLYWAY_DBMS, FLYWAY_HOST, FLYWAY_PORT and FLYWAY_DATABASE_NAME
* FLYWAY_DRIVER: Fully qualified classname of the jdbc driver (autodetected by default based on flyway.url)
* FLYWAY_USER: database user, default to 'data'.
* FLYWAY_PASSWORD: database password, default to 'data'.
* FLYWAY_SCHEMAS: Optional, comma-separated list of schemas managed by Flyway
* FLYWAY_TABLE: Optional, name of Flyway's metadata table (default: schema_version)
* DATASETS: column-separated list of datasets to load. Each dataset should have a descriptor defined as a Java properties file (\<dataset\>\_dataset.properties) located in a jar under eu.humanbrainproject.mip.migrations package.

## Build

Run: `./build.sh`

## Publish on Docker Hub

Run: `./publish.sh`

## License

### data-db-setup

(this project)

Copyright (C) 2017 [LREN CHUV](https://www.unil.ch/lren/en/home.html)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.

### Flyway

Copyright (C) 2016-2017 [Boxfuse GmbH](https://boxfuse.com)

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

http://www.apache.org/licenses/LICENSE-2.0

## Trademark
Flyway is a registered trademark of [Boxfuse GmbH](https://boxfuse.com).
