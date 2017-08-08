package eu.humanbrainproject.mip.migrations;

import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.MigrationChecksumProvider;
import org.flywaydb.core.api.migration.MigrationInfoProvider;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ParseDouble;
import org.supercsv.cellprocessor.ParseInt;
import org.supercsv.cellprocessor.constraint.UniqueHashCode;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.ICsvListReader;
import org.supercsv.prefs.CsvPreference;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

@SuppressWarnings("unused")
public class R__CreateViews implements JdbcMigration {

    private static final Logger LOG = Logger.getLogger("Create views");

    public void migrate(Connection connection) throws Exception {
    }

}
