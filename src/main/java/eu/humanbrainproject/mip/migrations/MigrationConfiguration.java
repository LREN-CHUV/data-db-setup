package eu.humanbrainproject.mip.migrations;

import eu.humanbrainproject.mip.migrations.datapackage.DataPackage;
import eu.humanbrainproject.mip.migrations.datapackage.Field;
import eu.humanbrainproject.mip.migrations.datapackage.Resource;
import eu.humanbrainproject.mip.migrations.datapackage.Schema;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.util.*;

public class MigrationConfiguration {

    private static final Logger LOG = LoggerFactory.getLogger("Configuration");

    private static final String TABLE_REGEX = "\\$\\{table}";

    private DataPackage datapackage;
    private Class<?> resourceBaseClass = this.getClass();

    public MigrationConfiguration() {
    }

    public MigrationConfiguration(Class<?> resourceBaseClass) {
        this.resourceBaseClass = resourceBaseClass;
    }

    DataPackage getDataPackage() {
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

    public DatasetConfiguration getDatasetConfiguration(String datasetName) {
        if (getDataPackage() != null) {
            return new DatasetConfigurationFromDataPackage(datasetName);
        } else {
            return new DatasetConfigurationFromProperties(datasetName);
        }
    }

    public DatasetConfiguration getTableConfiguration(String tableName) {
        try {
            for (String dataset: getDatasets()) {
                DatasetConfiguration datasetConfiguration = getDatasetConfiguration(dataset);
                    if (datasetConfiguration.getDatasetTableName().equals(tableName)) {
                        return datasetConfiguration;
                    }
            }
        } catch (IOException e) {
            LOG.error("Configuration problem: " + e.getMessage(), e);
        }
        throw new RuntimeException("Cannot find configuration for table " + tableName);
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

    public String getConfigResourcePath(String path) {
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

    public interface DatasetConfiguration {

        String getDatasetCsvFilePath() throws IOException;

        String getDatasetTableName() throws IOException;

        String getDatasetDeleteQuery() throws IOException;

        String getDatasetPrimaryKey() throws IOException;

        List<Field> getFields() throws IOException;

    }

    class DatasetConfigurationFromProperties implements DatasetConfiguration {

        private final Map<String, Properties> datasetProperties = new HashMap<>();
        private final Map<String, Properties> tableColumns = new HashMap<>();

        private String datasetName;

        DatasetConfigurationFromProperties(String datasetName) {
            this.datasetName = datasetName;
        }

        @Override
        public String getDatasetCsvFilePath() throws IOException {
            final Properties dataset = getDatasetProperties();

            final String csvFileName = dataset.getProperty("__CSV_FILE", "/data/values.csv");
            return getDataResourcePath(csvFileName);
        }

        @Override
        public String getDatasetTableName() throws IOException {
            final Properties dataset = getDatasetProperties();

            final String tableName = dataset.getProperty("__TABLE", "");

            if (tableName == null) {
                throw new IllegalArgumentException("__TABLE properties is not defined for dataset " + datasetName);
            }

            return tableName;
        }

        @Override
        public String getDatasetDeleteQuery() throws IOException {
            final Properties dataset = getDatasetProperties();
            final String tableName = getDatasetTableName();

            return dataset.getProperty("__DELETE_SQL", "DELETE FROM \"" + tableName + "\"")
                    .replaceFirst(TABLE_REGEX, tableName);
        }

        @Override
        public String getDatasetPrimaryKey() throws IOException {
            final String tableName = getDatasetTableName();
            final Properties columns = getColumnsProperties(tableName);
            String columnsStr = columns.getProperty("__COLUMNS");

            for (String column: StringUtils.split(columnsStr, ",")) {

                if (columns.getProperty(column + ".constraints", "").equals("is_index")) {
                    return column;
                }
            }
            return null;
        }

        @Override
        public List<Field> getFields() throws IOException {
            final String tableName = getDatasetTableName();
            final Properties columns = getColumnsProperties(tableName);

            String columnsStr = columns.getProperty("__COLUMNS");
            List<Field> fields = new ArrayList<>();

            for (String column: StringUtils.split(columnsStr, ",")) {

                String sqlType = columns.getProperty(column + ".type", "VARCHAR");
                if (columns.getProperty(column + ".type") == null) {
                    LOG.warn("Column type for " + column + " is not defined in columns.properties");
                }

                Field field = new Field();
                field.setName(column);
                field.setSqlType(sqlType);

                fields.add(field);
            }
            return fields;
        }

        private Properties getDatasetProperties() throws IOException {
            Properties datasetProperties = this.datasetProperties.get(datasetName);
            if (datasetProperties == null) {
                datasetProperties = new Properties();
                InputStream datasetResource = getDatasetResource(datasetName);
                datasetProperties.load(datasetResource);
                this.datasetProperties.put(datasetName, datasetProperties);
            }
            return datasetProperties;
        }

        private Properties getColumnsProperties(String tableName) throws IOException {
            Properties columnsProperties = tableColumns.get(tableName);
            if (columnsProperties == null) {
                columnsProperties = new Properties();
                InputStream datasetResource = getColumnsResource(tableName);
                columnsProperties.load(datasetResource);
                tableColumns.put(tableName, columnsProperties);
            }
            return columnsProperties;
        }

        private InputStream getColumnsResource(String tableName) {
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

    }

    class DatasetConfigurationFromDataPackage implements DatasetConfiguration {

        private String datasetName;

        DatasetConfigurationFromDataPackage(String datasetName) {
            this.datasetName = datasetName;
        }

        @Override
        public String getDatasetCsvFilePath() {
            return getDataPackage().getResource(datasetName).getPath();
        }

        @Override
        public String getDatasetTableName() {
            final String tableName = getDataPackage().getResource(datasetName).getSchema().getTableName();

            if (tableName == null) {
                throw new IllegalArgumentException("tableName property is not defined in the schema for dataset " + datasetName);
            }

            return tableName;
        }

        @Override
        public String getDatasetDeleteQuery() {
            final Resource resource = getDataPackage().getResource(datasetName);
            String query = resource.getDeleteQuery();
            if (query == null) {
                final Schema schema = resource.getSchema();
                if (schema.getDatasetKey() == null) {
                    return "DELETE FROM \"" + schema.getTableName() + "\"";
                } else {
                    return "DELETE FROM \"" + schema.getTableName() + "\" WHERE \"" + schema.getDatasetKey() + "\" = '" + resource.computeDataset() + "'";
                }
            }
            return query;
        }

        @Override
        public String getDatasetPrimaryKey() {
            return getDataPackage().getResource(datasetName).getSchema().getPrimaryKey();
        }

        @Override
        public List<Field> getFields() {
            return getDataPackage().getResource(datasetName).getSchema().getFields();
        }

    }

}
