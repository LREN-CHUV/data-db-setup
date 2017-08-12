package eu.humanbrainproject.mip.migrations;

import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;

abstract class MipMigration implements JdbcMigration {

    private final Map<String, Properties> tableColumns = new HashMap<>();
    private final Map<String, Properties> datasetProperties = new HashMap<>();

    String[] getDatasets() {
        String datasetsStr = System.getenv("DATASETS");
        if (datasetsStr == null) {
            datasetsStr = "default";
        }
        return datasetsStr.split(",");
    }

    Properties getDatasetProperties(String datasetName) throws IOException {
        Properties datasetProperties = this.datasetProperties.get(datasetName);
        if (datasetProperties == null) {
            datasetProperties = new Properties();
            InputStream datasetResource = getDatasetResource(datasetName);
            datasetProperties.load(datasetResource);
            this.datasetProperties.put(datasetName, datasetProperties);
        }
        return datasetProperties;
    }

    InputStream getDatasetResource(String datasetName) {
        String propertiesFile = (datasetName == null) ? "dataset.properties" : datasetName + "_dataset.properties";
        InputStream datasetResource = getClass().getResourceAsStream(propertiesFile);
        if (datasetResource == null) {
            throw new IllegalStateException("Cannot load resource from " +
                    getClass().getPackage().getName().replaceAll("\\.", "/") +
                    "/" + propertiesFile + ". Check DATASETS environment variable and contents of the jar");
        }
        return datasetResource;
    }

    Properties getColumnsProperties(String tableName) throws IOException {
        Properties columnsProperties = tableColumns.get(tableName);
        if (columnsProperties == null) {
            columnsProperties = new Properties();
            InputStream datasetResource = getColumnsResource(tableName);
            columnsProperties.load(datasetResource);
            tableColumns.put(tableName, columnsProperties);
        }
        return columnsProperties;
    }

    InputStream getColumnsResource(String tableName) {
        String propertiesFile = (tableName == null) ? "columns.properties" : tableName.toLowerCase() + "_columns.properties";
        InputStream datasetResource = getClass().getResourceAsStream(propertiesFile);
        if (datasetResource == null && getDatasets().length == 1) {
            propertiesFile = "columns.properties";
            datasetResource = getClass().getResourceAsStream(propertiesFile);
        }
        if (datasetResource == null) {
            throw new IllegalStateException("Cannot load resource from " +
                    getClass().getPackage().getName().replaceAll("\\.", "/") +
                    "/" + propertiesFile + ". Check DATASETS environment variable and contents of the jar");
        }
        return datasetResource;
    }

    List<String> getColumns(String tableName) throws IOException {
        Properties columnsDef = getColumnsProperties(tableName);
        String columnsStr = columnsDef.getProperty("__COLUMNS");
        return Arrays.asList(StringUtils.split(columnsStr, ","));
    }

    List<String> getIdColumns(String tableName) throws IOException {
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

}
