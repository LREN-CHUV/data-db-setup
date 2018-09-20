package eu.humanbrainproject.mip.migrations.values;

import eu.humanbrainproject.mip.migrations.MipMigration;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.MigrationChecksumProvider;
import org.flywaydb.core.api.migration.MigrationInfoProvider;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;

public abstract class AbstractDatasetSetup extends MipMigration implements JdbcMigration, MigrationInfoProvider, MigrationChecksumProvider {

    @Override
    public void migrate(Connection connection) throws Exception {
        String[] datasets = getDatasets();
        if (datasets.length == 1 && "".equals(datasets[0])) {
            if (getDatapackage() == null) {
                getLogger().info("No dataset defined, we will not setup dataset values.");
            }
            return;
        }
        try {

            connection.setAutoCommit(false);

            for (String dataset : datasets) {
                getLogger().info("Migrating dataset " + dataset + "...");
                loadDataset(connection, dataset);
            }

            connection.commit();

        } catch (java.sql.BatchUpdateException e) {
            getLogger().log(Level.SEVERE, "Cannot migrate data", e);
            if (e.getNextException() != null) {
                getLogger().log(Level.SEVERE, "Caused by", e.getNextException());
            }
        } catch (Exception e) {
            getLogger().log(Level.SEVERE, "Cannot migrate data", e);
            if (e.getCause() != null) {
                getLogger().log(Level.SEVERE, "Caused by", e.getCause());
            }
            throw e;
        }
    }

    @Override
    public Integer getChecksum() {
        int checksum = 0;
        for (String dataset : getDatasets()) {
            checksum += computeChecksum(dataset, getLogger());
        }
        return checksum;
    }

    private int computeChecksum(String dataset, Logger log) {
        final CRC32 crc32 = new CRC32();

        // Use the name of the dataset
        byte[] bytes = dataset.getBytes();
        crc32.update(bytes, 0, bytes.length);

        // Use the values in the dataset
        InputStream datasetResource = getDatasetResource(dataset);
        byte[] data = new byte[1024];
        int read;
        try {
            while ((read = datasetResource.read(data)) > 0) {
                crc32.update(data, 0, read);
            }
        } catch (IOException e) {
            log.log(Level.WARNING, "Cannot read data from dataset " + dataset, e);
        }

        return (int) crc32.getValue();
    }


    @Override
    public MigrationVersion getVersion() {
        return null;
    }

    @Override
    public String getDescription() {
        String[] datasets = getDatasets();
        return "Setup dataset" + (datasets.length > 1 ? "s " : " ") + StringUtils.join(datasets, ',');
    }

    protected abstract Logger getLogger();
}
