package eu.humanbrainproject.mip.migrations;

import eu.humanbrainproject.mip.migrations.datapackage.DataPackage;
import eu.humanbrainproject.mip.migrations.datapackage.Resource;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.io.*;
import java.util.*;

public abstract class MipMigration implements JdbcMigration {

    private final Map<String, Properties> tableColumns = new HashMap<>();
    private final Map<String, Properties> datasetProperties = new HashMap<>();

    private DataPackage datapackage;

    protected DataPackage getDataPackage() {
        String datapackageStr = System.getenv("DATAPACKAGE");
        if (datapackage == null && datapackageStr != null && !datapackageStr.isEmpty()) {
            if (!existsDataResource(datapackageStr)) {
                throw new IllegalStateException("Cannot load data package descriptor from " + getDataResourcePath(datapackageStr) +
                        ". Check DATAPACKAGE environment variable and contents of the Docker image");
            }
            datapackage = DataPackage.load(getDataResourcePath(datapackageStr));
        }
        return datapackage;
    }

    protected String[] getDatasets() {
        if (getDataPackage() != null) {
            return getDataPackage().getResources().stream().map(Resource::getName).toArray(String[]::new);
        }

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
        if (getDataPackage() != null) {
            String path = getDataPackage().getResource(datasetName).getPath();
            if (!existsDataResource(path)) {
                throw new IllegalStateException("Cannot load resource from " + getDataResourcePath(path) +
                        ". Check datapackage.json descriptor and contents of the Docker image");
            }
            return getDataResource(path);
        }

        String propertiesFile = (datasetName == null) ? "dataset.properties" : datasetName + "_dataset.properties";
        if (!existsConfigResource(propertiesFile)) {
            throw new IllegalStateException("Cannot load resource from " + getConfigResourcePath(propertiesFile) +
                    ". Check DATASETS environment variable and contents of the Docker image");
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
            throw new IllegalStateException("Cannot load resource from " + getConfigResourcePath(propertiesFile) +
                    ". Check DATASETS environment variable and contents of the Docker image");
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

        for (String column : columns) {
            if (columnsDef.getProperty(column + ".constraints", "").equals("is_index") ||
                    columnsDef.getProperty(column + ".is_index", "").equals("true")) {
                ids.add(column);
            }
        }
        return ids;
    }

    private String getConfigResourcePath(String path) {
        if (!path.startsWith("/")) {
            return "/flyway/config/" + path;
        }
        return path;
    }

    protected boolean existsConfigResource(String name) {
        if (getClass().getResource(name) != null) {
            return true;
        }

        final File configFile = new File(getConfigResourcePath(name));

        return configFile.canRead();
    }

    protected InputStream getConfigResource(String name) {
        if (getClass().getResource(name) != null) {
            return getClass().getResourceAsStream(name);
        }

        final File configFile = new File(getConfigResourcePath(name));
        try {
            return new FileInputStream(configFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Cannot read file " + configFile.getAbsolutePath());
        }
    }

    protected String getDataResourcePath(String path) {
        if (!path.startsWith("/")) {
            return "/data/" + path;
        }
        return path;
    }

    private boolean existsDataResource(String name) {
        if (getClass().getResource(name) != null) {
            return true;
        }

        final File dataFile = new File(getDataResourcePath(name));

        return dataFile.canRead();
    }

    private InputStream getDataResource(String name) {
        if (getClass().getResource(name) != null) {
            return getClass().getResourceAsStream(name);
        }

        final File dataFile = new File(getDataResourcePath(name));
        try {
            return new FileInputStream(dataFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Cannot read file " + dataFile.getAbsolutePath());
        }
    }

}
