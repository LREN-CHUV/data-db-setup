package eu.humanbrainproject.mip.migrations.values;

import eu.humanbrainproject.mip.migrations.datapackage.Field;
import eu.humanbrainproject.mip.migrations.datapackage.Resource;
import eu.humanbrainproject.mip.migrations.datapackage.Schema;
import org.apache.commons.lang3.StringUtils;

import java.util.List;
import java.util.logging.Logger;

/**
 * Setup values using the definition from Data-package
 */
@SuppressWarnings("unused")
public class R__SetupDataPackage extends AbstractDatasetSetup {

    private static final Logger LOG = Logger.getLogger("Setup data-package");

    @Override
    public boolean isUndo() {
        return false;
    }

    @Override
    public String getDescription() {
        if (getDataPackage() == null) {
            return "Skipped - Setup datapackage";
        }
        String[] datasets = getDatasets();
        return "Setup dataset" + (datasets.length > 1 ? "s " : " ") + StringUtils.join(datasets, ',');
    }

    @Override
    protected boolean shouldAttemptMigration(String[] datasets) {
        if (getDataPackage() == null) {
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
        return getDataPackage().getResource(datasetName).getPath();
    }

    @Override
    protected String getDatasetTableName(String datasetName) {
        return getDataPackage().getResource(datasetName).getSchema().getTableName();
    }

    @Override
    protected String getDatasetDeleteQuery(String datasetName) {
        final Resource resource = getDataPackage().getResource(datasetName);
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
        return getDataPackage().getResource(datasetName).getSchema().getPrimaryKey();
    }

    @Override
    protected List<Field> getFields(String datasetName) {
        return getDataPackage().getResource(datasetName).getSchema().getFields();
    }

    @Override
    protected Logger getLogger() {
        return LOG;
    }
}

