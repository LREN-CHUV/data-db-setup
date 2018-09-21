package eu.humanbrainproject.mip.migrations.values;

import eu.humanbrainproject.mip.migrations.datapackage.Field;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.logging.Logger;

@SuppressWarnings("unused")
public class R__SetupValues extends AbstractDatasetSetup {

    private static final Logger LOG = Logger.getLogger("Setup values");

    @Override
    public boolean isUndo() {
        return false;
    }

    @Override
    public String getDescription() {
        if (getDataPackage() != null) {
            return "Skipped - Setup values from properties file";
        }
        String[] datasets = getDatasets();
        return "Setup dataset" + (datasets.length > 1 ? "s " : " ") + StringUtils.join(datasets, ',');
    }

    @Override
    protected boolean shouldAttemptMigration(String[] datasets) {
        if (getDataPackage() != null) {
            return false;
        }
        if (datasets.length == 1 && "".equals(datasets[0])) {
            getLogger().info("No dataset defined, we will not setup dataset values.");
            return false;
        } else {
            return true;
        }
    }

    protected String getDatasetCsvFilePath(String datasetName) throws IOException {
        final Properties dataset = getDatasetProperties(datasetName);

        final String csvFileName = dataset.getProperty("__CSV_FILE", "/data/values.csv");
        return getDataResourcePath(csvFileName);
    }

    protected String getDatasetTableName(String datasetName) throws IOException {
        final Properties dataset = getDatasetProperties(datasetName);

        final String tableName = dataset.getProperty("__TABLE", "");

        if (tableName == null) {
            throw new IllegalArgumentException("__TABLE properties is not defined for dataset " + datasetName);
        }

        return tableName;
    }

    protected String getDatasetDeleteQuery(String datasetName) throws IOException {
        final Properties dataset = getDatasetProperties(datasetName);
        final String tableName = getDatasetTableName(datasetName);

        return dataset.getProperty("__DELETE_SQL", "DELETE FROM " + tableName)
                .replaceFirst(TABLE_REGEX, tableName);
    }

    protected String getDatasetPrimaryKey(String datasetName) throws IOException {
        final Properties dataset = getDatasetProperties(datasetName);
        final String tableName = getDatasetTableName(datasetName);
        final Properties columns = getColumnsProperties(tableName);
        String columnsStr = columns.getProperty("__COLUMNS");

        for (String column: StringUtils.split(columnsStr, ",")) {

            if (columns.getProperty(column + ".constraints", "").equals("is_index")) {
                return column;
            }
        }
        return null;
    }

    protected List<Field> getFields(String datasetName) throws IOException {
        final String tableName = getDatasetTableName(datasetName);
        final Properties columns = getColumnsProperties(tableName);

        String columnsStr = columns.getProperty("__COLUMNS");
        List<Field> fields = new ArrayList<>();

        for (String column: StringUtils.split(columnsStr, ",")) {

            String sqlType = columns.getProperty(column + ".type", "VARCHAR");
            if (columns.getProperty(column + ".type") == null) {
                getLogger().warning("Column type for " + column + " is not defined in columns.properties");
            }

            Field field = new Field();
            field.setName(column);
            field.setSqlType(sqlType);

            fields.add(field);
        }
        return fields;
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }

}
