package eu.humanbrainproject.mip.migrations;

import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.io.*;
import java.util.*;

public abstract class MipMigration implements JdbcMigration {

    private final Map<String, Properties> tableColumns = new HashMap<>();
    private final Map<String, Properties> datasetProperties = new HashMap<>();

    protected String[] getDatasets() {
        String datasetsStr = System.getenv("DATASETS");
        if (datasetsStr == null || "".equals(datasetsStr.trim())) {
            return new String[0];
        }
        return datasetsStr.trim().split(",");
    }

    protected Properties getDatasetProperties(String datasetName) throws IOException {
        Properties datasetProperties = this.datasetProperties.get(datasetName);
        if (datasetProperties == null) {
            datasetProperties = new Properties();
            InputStream datasetResource = getDatasetResource(datasetName);
            datasetProperties.load(datasetResource);
            this.datasetProperties.put(datasetName, datasetProperties);
        }
        return datasetProperties;
    }

    protected InputStream getDatasetResource(String datasetName) {
        String propertiesFile = (datasetName == null) ? "dataset.properties" : datasetName + "_dataset.properties";
        if (!existsConfigResource(propertiesFile)) {
            throw new IllegalStateException("Cannot load resource from /config/" + propertiesFile +
              ". Check DATASETS environment variable and contents of the jar");
        }
        return getConfigResource(propertiesFile);
    }

    protected Properties getColumnsProperties(String tableName) throws IOException {
        Properties columnsProperties = tableColumns.get(tableName);
        if (columnsProperties == null) {
            columnsProperties = new Properties();
            InputStream datasetResource = getColumnsResource(tableName);
            columnsProperties.load(datasetResource);
            tableColumns.put(tableName, columnsProperties);
        }
        return columnsProperties;
    }

    protected InputStream getColumnsResource(String tableName) {
        String propertiesFile = (tableName == null) ? "columns.properties" : tableName.toLowerCase() + "_columns.properties";

        if (!existsConfigResource(propertiesFile) && getDatasets().length == 1) {
            if (existsConfigResource("columns.properties")) {
                propertiesFile = "columns.properties";
            }
        }

        if (!existsConfigResource(propertiesFile)) {
            throw new IllegalStateException("Cannot load resource from /config/" + propertiesFile +
              ". Check DATASETS environment variable and contents of the jar");
        }
        return getConfigResource(propertiesFile);
    }

    protected List<String> getColumns(String tableName) throws IOException {
        Properties columnsDef = getColumnsProperties(tableName);
        String columnsStr = columnsDef.getProperty("__COLUMNS");
        return Arrays.asList(StringUtils.split(columnsStr, ","));
    }

    protected List<String> getIdColumns(String tableName) throws IOException {
        Properties columnsDef = getColumnsProperties(tableName);
        List<String> columns = getColumns(tableName);
        List<String> ids = new ArrayList<>();

        for (String column: columns) {
            if (columnsDef.getProperty(column + ".constraints", "").equals("is_index") ||
                columnsDef.getProperty(column + ".is_index", "").equals("true")) {
                ids.add(column);
            }
        }
        return ids;
    }

    protected boolean existsConfigResource(String name) {
        if (getClass().getResource(name) != null) {
            return true;
        }

        final File configFile = new File("/flyway/config/" + name );

        return configFile.canRead();
    }

    protected InputStream getConfigResource(String name) {
        if (getClass().getResource(name) != null) {
            return getClass().getResourceAsStream(name);
        }

        final File configFile = new File("/flyway/config/" + name );
        try {
            return new FileInputStream(configFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Cannot read file /flyway/config/" + name);
        }
    }
}
