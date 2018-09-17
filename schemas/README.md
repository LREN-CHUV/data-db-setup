# Schemas

This folder contains the schema describing tabular data [Tabular Data Package](https://frictionlessdata.io/specs/tabular-data-package/) version 1.0.0-rc.2.

In this specification, it's assumed that the tabular data comes as a set of CSV files, but to create the database tables and their columns, we extend the specification with the following points:

* each resource will generate one database table,
* the name of the resource is the name of the table
* in the schema for each field, we define an additional and optional property: `sqlType`, whose value is the SQL type of the field in the database. If it is not defined for a field, we use a standard mapping for the SQL type based on the field type as described in the schema.
