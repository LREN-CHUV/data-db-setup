[![CHUV](https://img.shields.io/badge/CHUV-LREN-AF4C64.svg)](https://www.unil.ch/lren/en/home.html) [![License](https://img.shields.io/badge/license-Apache--2.0-blue.svg)](https://github.com/LREN-CHUV/data-db-setup/blob/master/LICENSE) [![DockerHub](https://img.shields.io/badge/docker-hbpmip%2Fdata--db--setup-008bb8.svg)](https://hub.docker.com/r/hbpmip/data-db-setup/) [![Codacy Badge](https://api.codacy.com/project/badge/Grade/fb70732c6b7647699610bdb7be1d8548)](https://www.codacy.com/app/hbpmip/data-db-setup?utm_source=github.com&amp;utm_medium=referral&amp;utm_content=LREN-CHUV/data-db-setup&amp;utm_campaign=Badge_Grade)
[![CircleCI](https://circleci.com/gh/LREN-CHUV/data-db-setup.svg?style=svg)](https://circleci.com/gh/LREN-CHUV/data-db-setup)

# Setup for database 'data-db'

## Introduction

This project uses [Flyway](http://flywaydb.org/) to manage the database migration scripts that will create and fill the data features tables containing research-grade data used by MIP algorithms for machine learning.

The research-grade data tables can contain the following types of data:

* the features (values) for each Common Data Elements (CDE) defined by MIP.
* the features extracted from research datasets (ADNI, PPMI...)
* the features extracted from clinical data

The data stored in the feature tables is labelled research-grade as it should have gone through a data curation process (description, registration, adaptation to fit the MIP CDE variables, cleaning...).

## Usage

`data-db` requires a running Postgres database with admin access where it will create new tables and store some data.

Run:

```console
$ docker run -i -t --rm -e FLYWAY_HOST=`hostname` hbpmip/data-db-setup:2.5.5 migrate
```

where the environment variables are:

* FLYWAY_HOST: database host, default to 'db'.
* FLYWAY_PORT: database port, default to 5432.
* FLYWAY_DATABASE_NAME: Optional, name of the database or schema, default to 'data'
* FLYWAY_URL: JDBC url to the database, constructed by default from FLYWAY_DBMS, FLYWAY_HOST, FLYWAY_PORT and FLYWAY_DATABASE_NAME
* FLYWAY_DRIVER: Optional, fully qualified classname of the jdbc driver (autodetected by default based on flyway.url)
* FLYWAY_USER: database user, default to 'data'.
* FLYWAY_PASSWORD: database password, default to 'data'.
* FLYWAY_SCHEMAS: Optional, comma-separated list of schemas managed by Flyway, default to 'public'
* FLYWAY_TABLE: Optional, name of Flyway's metadata table (default: schema_version)
* DATASETS: (deprecated) column-separated list of datasets to load. Each dataset should have a descriptor defined as a Java properties file (\<dataset\>\_dataset.properties) located in /config folder.
* DATAPACKAGE: column-separated list of datapackage.json files to load. This is an alternative method to describing datasets using properties files. Datapackage.json file should be located in the /data folder
* VIEWS: column-separated list of views to create. Each view should have a descriptor defined as a Java properties file (\<view\>\_view.properties) located in /config folder,
  as well as a SQL template whose name is defined with the property \_\_SQL_TEMPLATE and that should be located in the same folder.
* AUTO_GENERATE_TABLES: if set to true, will attempt to generate the tables from the datapackage definition. Use this method only for development or quick prototyping, as tables should normally be created using SQL migrations managed by Flyway.
* LOG_LEVEL: desired log level, default is 'info', use 'debug' for more verbose output

## Customizing the data tables

`data-db-setup` does not provide much by itself, you need to customise it to your needs.

You need to create a new project that will contain the following elements:

* a Dockerfile that inherit from hbpmip/data-db-setup
* a set of SQL migration scripts that will create the data tables and views, to be managed by [Flyway](http://flywaydb.org/) and included in the Docker image
* optionally, CSV files containing data to upload into the database if that data is publishable (1)
* a description of the structure of the data tables in the [Frictionlessdata package format](https://frictionlessdata.io/specs/)

You can use the command `atomist create data db setup` from [MIP SDM](https://github.com/LREN-CHUV/mip-sdm) to generate a skeleton of this new project for you.

The Dockerfile for the specialised image should look like:

Dockerfile
```dockerfile
  # Final image
  FROM hbpmip/data-db-setup:2.5.5

  ARG BUILD_DATE
  ARG VCS_REF
  ARG VERSION

  COPY data/empty.csv data/datapackage.json /data/
  COPY sql/V1_0__create.sql /flyway/sql/
  COPY docker/run.sh /

  RUN chmod +x /run.sh

  ENV IMAGE=hbpmip/my-data-db-setup:1.0.0 \
      DATAPACKAGE=/data/datapackage.json

```

The following environment variables should be defined statically by child images of data-db-setup:

* IMAGE: name of this Docker image, including version (for help message)
* DATASETS: (deprecated) column-separated list of datasets to load.
* DATAPACKAGE: column-separated list of datapackage.json files to load. This is an alternative method to describing datasets using properties files.
* VIEWS: column-separated list of views to create. Each view should have a descriptor defined as a Java properties file (\<view\>\_view.properties) located in /config folder,
  as well as a SQL template whose name is defined with the property \_\_SQL_TEMPLATE and that should be located in the same folder.
* AUTO_GENERATE_TABLES: if set to true, will attempt to generate the tables from the datapackage definition. Use this method only for development or quick prototyping, as tables should normally be created using SQL migrations managed by Flyway.

Note (1): a Docker image can be seen as a Zip file, it's perfectly reasonable to store data in it, as long as you do not attempt to store several Gigabytes of data. Most Docker images weight a few hundred MB, adding data from CSV files is reasonable.
If the data cannot be published openly, you can rely on private Docker registries such as those provided by [Gitlab.com](https://gitlab.com) that provide a secured and password-protected storage for your Docker image and its data or binaries.

## Build

Run: `./build.sh`

This command will build the Docker image hbpmip/data-db-setup produced by this project

## Testing

```
  ./test/test.sh
```

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

# Acknowledgements

This work has been funded by the European Union Seventh Framework Program (FP7/2007­2013) under grant agreement no. 604102 (HBP)

This work is part of SP8 of the Human Brain Project (SGA1).
