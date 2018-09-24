package eu.humanbrainproject.mip.migrations;

import eu.humanbrainproject.mip.migrations.datapackage.Field;
import eu.humanbrainproject.mip.migrations.datapackage.Resource;
import eu.humanbrainproject.mip.migrations.datapackage.Schema;
import org.flywaydb.core.api.callback.Callback;
import org.flywaydb.core.api.callback.Context;
import org.flywaydb.core.api.callback.Event;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.SQLException;

@SuppressWarnings("unused")
public class GenerateTablesCallback implements Callback {

    private static final Logger LOG = LoggerFactory.getLogger("Auto-generate tables");

    protected final MigrationConfiguration config = new MigrationConfiguration();

    @Override
    public boolean canHandleInTransaction(Event event, Context context) {
        return true;
    }

    @Override
    public boolean supports(Event event, Context context) {
        return event == Event.BEFORE_MIGRATE;
    }

    @Override
    public void handle(Event event, Context context) {

        if (event == Event.BEFORE_MIGRATE
                && config.getDataPackage() != null
                && ("true".equals(System.getenv("AUTO_GENERATE_TABLES")) || "yes".equals(System.getenv("AUTO_GENERATE_TABLES")))) {

            LOG.warn("Starting the automatic generation of tables from their definition");
            LOG.warn("Automatic generation is experimental and should only be used during development phase.");
            LOG.warn("");
            generateTables(context.getConnection());
        }
    }

    private void generateTables(Connection connection) {

        for (Resource resource: config.getDataPackage().getResources()) {
            if (resource.getSchema().getTableName() == null) {
                throw new IllegalArgumentException("tableName property is not defined in the schema for dataset " + resource.getName());
            }
            generateTable(connection, resource.getSchema());
        }
    }

    private void generateTable(Connection connection, Schema schema) {

        StringBuilder sb = new StringBuilder();
        sb.append("SET datestyle to 'European';");
        sb.append("CREATE TABLE IF NOT EXISTS ");
        sb.append("\"").append(schema.getTableName()).append("\" (\n");
        for (Field field: schema.getFields()) {
            sb.append("\"").append(field.getName()).append("\" ");
            sb.append(field.getSqlType()).append(",\n");
        }

        if (schema.getPrimaryKey() != null && !"".equals(schema.getPrimaryKey())) {
            sb.append("CONSTRAINT pk_").append(schema.getTableName());
            sb.append(" PRIMARY KEY (").append(schema.getPrimaryKey()).append(")\n");
        } else {
            sb = new StringBuilder(sb.substring(0, sb.length() - 2)).append("\n");
        }

        sb.append(")\n");
        sb.append("WITH (OIDS=FALSE)\n");
        sb.append(";\n");

        String createDDL = sb.toString();

        LOG.warn("Generating table " + schema.getTableName() + " using SQL DDL:");
        LOG.warn(createDDL);


        try {
            connection.createStatement().execute(createDDL);
        } catch (SQLException e) {
            LOG.error("Cannot create table " + schema.getTableName());
            throw new RuntimeException(e);
        }

    }
}
