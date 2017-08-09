package eu.humanbrainproject.mip.migrations;

import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

abstract class MipMigration implements JdbcMigration {

    private Map<String, Properties> tableColumns = new HashMap<>();
    private Map<String, Properties> datasetProperties = new HashMap<>();

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
            throw new RuntimeException("Cannot load resource from " + getClass().getPackage().getName() +
                    "." + propertiesFile + ". Check DATASETS environment variable and contents of the jar");
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
            throw new RuntimeException("Cannot load resource from " + getClass().getPackage().getName() +
                    "." + propertiesFile + ". Check DATASETS environment variable and contents of the jar");
        }
        return datasetResource;
    }

}
