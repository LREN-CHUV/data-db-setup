package eu.humanbrainproject.mip.migrations.values;

import eu.humanbrainproject.mip.migrations.datapackage.Field;
import eu.humanbrainproject.mip.migrations.datapackage.Resource;
import eu.humanbrainproject.mip.migrations.datapackage.Schema;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Setup values using the definition from Data-package
 */
@SuppressWarnings("unused")
public class R__SetupDataPackage extends AbstractDatasetSetup {

    private static final Logger LOG = LoggerFactory.getLogger("Setup data-package");

    @Override
    public boolean isUndo() {
        return false;
    }

    @Override
    public String getDescription() {
        if (config.getDataPackage() == null) {
            return "Skipped - Setup datapackage";
        }
        String[] datasets = config.getDatasets();
        return "Setup dataset" + (datasets.length > 1 ? "s " : " ") + StringUtils.join(datasets, ',');
    }

    @Override
    protected boolean shouldAttemptMigration(String[] datasets) {
        if (config.getDataPackage() == null) {
            return false;
        }
        if (datasets.length == 1 && "".equals(datasets[0])) {
            getLogger().info("No dataset defined, we will not setup dataset values.");
            return false;
        } else {
            return true;
        }
    }

    @Override
    protected String getDatasetCsvFilePath(String datasetName) {
        return config.getDataPackage().getResource(datasetName).getPath();
    }

    @Override
    protected String getDatasetTableName(String datasetName) {
        final String tableName = config.getDataPackage().getResource(datasetName).getSchema().getTableName();

        if (tableName == null) {
            throw new IllegalArgumentException("tableName property is not defined in the schema for dataset " + datasetName);
        }

        return tableName;
    }

    @Override
    protected String getDatasetDeleteQuery(String datasetName) {
        final Resource resource = config.getDataPackage().getResource(datasetName);
        String query = resource.getDeleteQuery();
        if (query == null) {
            final Schema schema = resource.getSchema();
            if (schema.getDatasetKey() == null) {
                return "DELETE FROM \"" + schema.getTableName() + "\"";
            } else {
                return "DELETE FROM \"" + schema.getTableName() + "\" WHERE \"" + schema.getDatasetKey() + "\" = '" + resource.getName() + "'";
            }
        }
        return query;
    }

    @Override
    protected String getDatasetPrimaryKey(String datasetName) {
        return config.getDataPackage().getResource(datasetName).getSchema().getPrimaryKey();
    }

    @Override
    protected List<Field> getFields(String datasetName) {
        return config.getDataPackage().getResource(datasetName).getSchema().getFields();
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}

