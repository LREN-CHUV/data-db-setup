package eu.humanbrainproject.mip.migrations.values;

import java.sql.Connection;
import java.util.logging.Level;
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
    protected Logger getLogger() {
        return LOG;
    }
}

