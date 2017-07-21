package eu.humanbrainproject.mip.migrations;

import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.flywaydb.core.api.migration.MigrationChecksumProvider;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ParseDouble;
import org.supercsv.cellprocessor.ParseInt;
import org.supercsv.cellprocessor.constraint.UniqueHashCode;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.ICsvListReader;
import org.supercsv.prefs.CsvPreference;

import java.io.FileReader;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@SuppressWarnings("unused")
public class R__SetupValues implements JdbcMigration, MigrationChecksumProvider {

    private static final Logger LOG = Logger.getLogger("Setup values");

    private static final String SQL_INSERT = "INSERT INTO ${table}(${keys}) VALUES(${values})";
    private static final String TABLE_REGEX = "\\$\\{table\\}";
    private static final String KEYS_REGEX = "\\$\\{keys\\}";
    private static final String VALUES_REGEX = "\\$\\{values\\}";
    private static final int BATCH_SIZE = 1000;

    public void migrate(Connection connection) throws Exception {
        String datasetsStr = System.getenv("DATASETS");
        if (datasetsStr == null) {
            datasetsStr = "default";
        }
        String[] datasets = datasetsStr.split(",");
        try {

            Properties columns = new Properties();
            columns.load(getClass().getResourceAsStream("columns.properties"));

            for (String dataset: datasets) {
                loadDataset(connection, columns, dataset);
            }

            connection.commit();
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Cannot migrate data", e);
            throw e;
        }
    }

    private void loadDataset(Connection connection, Properties columns, String datasetName) throws IOException, SQLException {
        Properties dataset = new Properties();
        InputStream datasetResource = getClass().getResourceAsStream(datasetName + "_dataset.properties");
        if (datasetResource == null) {
            throw new RuntimeException("Cannot load resource from " + getClass().getPackage().getName() +
                    "." + datasetName + "_dataset.properties. Check DATASETS environment variable and contents of the jar");
        }
        dataset.load(datasetResource);

        final String csvFileName = dataset.getProperty("__CSV_FILE", "/data/values.csv");
        final String tableName = dataset.getProperty("__TABLE", columns.getProperty("__TABLE"));
        final String deleteSql = dataset.getProperty("__DELETE_SQL", "DELETE FROM " + tableName)
                .replaceFirst(TABLE_REGEX, tableName);

        try (ICsvListReader csvReader = new CsvListReader(new FileReader(csvFileName), CsvPreference.STANDARD_PREFERENCE)) {

            // skip the header
            final String[] header = csvReader.getHeader(true);
            final CellProcessor[] processors = getProcessors(columns, header);

            String questionMarks = StringUtils.repeat("?,", header.length);
            questionMarks = (String) questionMarks.subSequence(0, questionMarks
                    .length() - 1);

            String query = SQL_INSERT.replaceFirst(TABLE_REGEX, tableName);
            query = query.replaceFirst(KEYS_REGEX, "\"" + StringUtils.join(header, "\",\"") + "\"");
            query = query.replaceFirst(VALUES_REGEX, questionMarks);

            try (PreparedStatement statement = connection.prepareStatement(query)) {
                connection.setAutoCommit(false);

                // Delete data from table before loading csv
                connection.createStatement().execute(deleteSql);

                List<Object> values;
                while ((values = csvReader.read(processors)) != null) {

                    int index = 1;
                    for (Object v : values) {
                        String column = header[index - 1];
                        String sqlType = columns.getProperty(column + ".type", "VARCHAR");
                        if (columns.getProperty(column + ".type") == null) {
                            LOG.warning("Column type for " + column + " is not defined in columns.properties");
                        }
                        if (v == null) {
                            statement.setNull(index, getSqlType(sqlType));
                        } else {
                            switch (getSqlType(sqlType)) {
                                case Types.CHAR:
                                case Types.VARCHAR:
                                    if (v instanceof String) {
                                      statement.setString(index, (String) v);
                                    } else {
                                      LOG.severe("On column " + column + ", String value expected, found " + v.getClass());
                                      throw new RuntimeException("On column " + column + ", String value expected, found " + v.getClass());
                                    }
                                    break;
                                case Types.INTEGER:
                                    if (v instanceof Integer) {
                                      statement.setInt(index, (Integer) v);
                                    } else {
                                      LOG.severe("On column " + column + ", Integer value expected, found " + v.getClass());
                                      throw new RuntimeException("On column " + column + ", Integer value expected, found " + v.getClass());
                                    }
                                    break;
                                case Types.NUMERIC:
                                    if (v instanceof Double) {
                                      statement.setDouble(index, (Double) v);
                                    } else {
                                      LOG.severe("On column " + column + ", Double value expected, found " + v.getClass());
                                      throw new RuntimeException("On column " + column + ", Double value expected, found " + v.getClass());
                                    }
                                    break;
                            }
                        }
                        index++;
                    }
                    statement.addBatch();
                    if (csvReader.getLineNumber() % BATCH_SIZE == 0) {
                        statement.executeBatch();
                    }
                }
                statement.executeBatch(); // insert remaining records
            }

        }
    }

    /**
     * Sets up the processors used for the examples. There are 10 CSV columns, so 10 processors are defined. Empty
     * columns are read as null (hence the NotNull() for mandatory columns).
     *
     * @param columnsDef
     * @return the cell processors
     */
    private static CellProcessor[] getProcessors(Properties columnsDef, String[] csvHeader) {

        String columnsStr = columnsDef.getProperty("__COLUMNS");
        List<String> columns = Arrays.asList(StringUtils.split(columnsStr, ","));

        if (csvHeader.length != columns.size()) {
            throw new RuntimeException("Mismatch between CSV file headers and declared list of columns: found " +
                csvHeader.length + " columns in CSV file, expected " + columns.size());
        }
        for (int i = 0; i < csvHeader.length; i++) {
            if (!csvHeader[i].equals(columns.get(i))) {
               throw new RuntimeException("Mismatch between CSV file headers and declared list of columns: found '" +
                  csvHeader[i] + "' in CVS headers, expected '" + columns.get(i) + "'");
            }
        }

        List<CellProcessor> processors = columns.stream().map(column -> {
            String colType = shortType(columnsDef.getProperty(column + ".type", "?"));
            if (columnsDef.getProperty(column + ".constraints", "").equals("is_index")) {
                if ("int".equals(colType)) {
                   return new ParseInt();
                } else {
                   return new UniqueHashCode();
                }
            } else {
                switch (colType) {
                    case "char":
                        return new Optional();
                    case "varchar":
                        return new Optional();
                    case "numeric":
                        return new Optional(new ParseDouble());
                    case "int":
                        return new Optional(new ParseInt());
                    default:
                        throw new RuntimeException("Unknown type " + colType + " on column " + column);
                }
            }

        }).collect(Collectors.toList());

        return processors.toArray(new CellProcessor[processors.size()]);
    }

    private static int getSqlType(String sqlType) {
        switch (shortType(sqlType)) {
            case "char":
                return Types.CHAR;
            case "varchar":
                return Types.VARCHAR;
            case "int":
                return Types.INTEGER;
            case "numeric":
                return Types.NUMERIC;
            default:
                throw new RuntimeException("Unknown SQL type: " + sqlType);
        }
    }

    private static String shortType(String sqlType) {
        return sqlType.replaceAll("\\(.*\\)", "").toLowerCase();
    }

    @Override
    public Integer getChecksum() {
        int checksum = 0;
        try {
            Properties columns = new Properties();
            columns.load(getClass().getResourceAsStream("columns.properties"));
            final String dataset = columns.getProperty("__DATASET", "unknown");
            checksum = computeChecksum(dataset);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot load columns.properties", e);
        }
        return checksum;
    }

    private static int computeChecksum(String dataset) {
        int checksum = 0;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");  // MD5 or SHA-1 or SHA-256
            byte[] bytes = dataset.getBytes();
            md.update(bytes, 0, bytes.length);
            byte[] digest = md.digest();
            for (Byte b: digest) {
                checksum += b.intValue();
            }
        } catch (NoSuchAlgorithmException e) {
            LOG.log(Level.WARNING, "Cannot compute checksum", e);
        }
        return checksum;
    }

}
