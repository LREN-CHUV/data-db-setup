package eu.humanbrainproject.mip.migrations;

import eu.humanbrainproject.mip.migrations.datapackage.DataPackage;
import eu.humanbrainproject.mip.migrations.datapackage.Resource;
import org.apache.commons.lang3.StringUtils;

import java.io.*;
import java.util.*;

public class MigrationConfiguration {

    private final Map<String, Properties> tableColumns = new HashMap<>();
    private final Map<String, Properties> datasetProperties = new HashMap<>();

    private DataPackage datapackage;
    private Class<?> resourceBaseClass = this.getClass();

    public MigrationConfiguration() {
    }

    public MigrationConfiguration(Class<?> resourceBaseClass) {
        this.resourceBaseClass = resourceBaseClass;
    }

    public DataPackage getDataPackage() {
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

    public String[] getDatasets() {
        if (getDataPackage() != null) {
            return getDataPackage().getResources().stream().map(Resource::getName).toArray(String[]::new);
        }

        String datasetsStr = System.getenv("DATASETS");
        if (datasetsStr == null || "".equals(datasetsStr.trim())) {
            return new String[0];
        }
        return datasetsStr.trim().split(",");
    }

    public Properties getDatasetProperties(String datasetName) throws IOException {
        Properties datasetProperties = this.datasetProperties.get(datasetName);
        if (datasetProperties == null) {
            datasetProperties = new Properties();
            InputStream datasetResource = getDatasetResource(datasetName);
            datasetProperties.load(datasetResource);
            this.datasetProperties.put(datasetName, datasetProperties);
        }
        return datasetProperties;
    }

    public InputStream getDatasetResource(String datasetName) {
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

    public Properties getColumnsProperties(String tableName) throws IOException {
        Properties columnsProperties = tableColumns.get(tableName);
        if (columnsProperties == null) {
            columnsProperties = new Properties();
            InputStream datasetResource = getColumnsResource(tableName);
            columnsProperties.load(datasetResource);
            tableColumns.put(tableName, columnsProperties);
        }
        return columnsProperties;
    }

    public InputStream getColumnsResource(String tableName) {
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

    public List<String> getColumns(String tableName) throws IOException {
        Properties columnsDef = getColumnsProperties(tableName);
        String columnsStr = columnsDef.getProperty("__COLUMNS");
        return Arrays.asList(StringUtils.split(columnsStr, ","));
    }

    public List<String> getIdColumns(String tableName) throws IOException {
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

    public boolean existsConfigResource(String name) {
        if (resourceBaseClass.getResource(name) != null) {
            return true;
        }

        final File configFile = new File(getConfigResourcePath(name));

        return configFile.canRead();
    }

    public InputStream getConfigResource(String name) {
        if (resourceBaseClass.getResource(name) != null) {
            return resourceBaseClass.getResourceAsStream(name);
        }

        final File configFile = new File(getConfigResourcePath(name));
        try {
            return new FileInputStream(configFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Cannot read file " + configFile.getAbsolutePath());
        }
    }

    public String getDataResourcePath(String path) {
        if (!path.startsWith("/")) {
            return "/data/" + path;
        }
        return path;
    }

    private boolean existsDataResource(String name) {
        if (resourceBaseClass.getResource(name) != null) {
            return true;
        }

        final File dataFile = new File(getDataResourcePath(name));

        return dataFile.canRead();
    }

    private InputStream getDataResource(String name) {
        if (resourceBaseClass.getResource(name) != null) {
            return resourceBaseClass.getResourceAsStream(name);
        }

        final File dataFile = new File(getDataResourcePath(name));
        try {
            return new FileInputStream(dataFile);
        } catch (FileNotFoundException e) {
            throw new RuntimeException("Cannot read file " + dataFile.getAbsolutePath());
        }
    }

}
