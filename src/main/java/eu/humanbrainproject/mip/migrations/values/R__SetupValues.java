package eu.humanbrainproject.mip.migrations.values;

import eu.humanbrainproject.mip.migrations.MipMigration;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.MigrationInfoProvider;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.flywaydb.core.api.migration.MigrationChecksumProvider;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.ParseDate;
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
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import java.io.IOException;
import java.util.zip.CRC32;

@SuppressWarnings("unused")
public class R__SetupValues extends MipMigration implements JdbcMigration, MigrationInfoProvider, MigrationChecksumProvider {

    private static final Logger LOG = Logger.getLogger("Setup values");

    private static final String SQL_INSERT = "INSERT INTO ${table}(${keys}) VALUES(${values})";
    private static final String TABLE_REGEX = "\\$\\{table}";
    private static final String KEYS_REGEX = "\\$\\{keys}";
    private static final String VALUES_REGEX = "\\$\\{values}";
    private static final int BATCH_SIZE = 1000;

    @Override
    public boolean isUndo() {
        return false;
    }

    public void migrate(Connection connection) throws Exception {
        String[] datasets = getDatasets();
        if (datasets.length == 1 && "".equals(datasets[0])) {
          LOG.info("No dataset defined, we will not setup dataset values.");
          return;
        }
        try {

            connection.setAutoCommit(false);

            for (String dataset : datasets) {
                LOG.info("Migrating dataset " + dataset + "...");
                loadDataset(connection, dataset);
            }

            connection.commit();

        } catch (java.sql.BatchUpdateException e) {
            LOG.log(Level.SEVERE, "Cannot migrate data", e);
            if (e.getNextException() != null) {
                LOG.log(Level.SEVERE, "Caused by", e.getNextException());
            }
        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Cannot migrate data", e);
            if (e.getCause() != null) {
                LOG.log(Level.SEVERE, "Caused by", e.getCause());
            }
            throw e;
        }
    }

    private void loadDataset(Connection connection, String datasetName) throws IOException, SQLException {
        final Properties dataset = getDatasetProperties(datasetName);

        final String csvFileName = dataset.getProperty("__CSV_FILE", "/data/values.csv");
        final String tableName = dataset.getProperty("__TABLE");
        if (tableName == null) {
            throw new IllegalArgumentException("__TABLE properties is not defined for dataset " + datasetName);
        }
        if (csvFileName.equals("/dev/null")) {
            LOG.warning("No data will be loaded in dataset " + datasetName);
            return;
        }

        final Properties columns = getColumnsProperties(tableName);
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
                        final int sqlTypeCode = getSqlType(sqlType);
                        if (v == null) {
                            statement.setNull(index, sqlTypeCode);
                        } else {
                            switch (sqlTypeCode) {
                                case Types.CHAR:
                                case Types.VARCHAR:
                                    if (v instanceof String) {
                                      statement.setString(index, (String) v);
                                    } else {
                                      LOG.severe("On column " + column + ", String value expected, found " + v.getClass());
                                      throw new IllegalArgumentException("On column " + column + ", String value expected, found " + v.getClass());
                                    }
                                    break;
                                case Types.INTEGER:
                                    if (v instanceof Integer) {
                                      statement.setInt(index, (Integer) v);
                                    } else {
                                      LOG.severe("On column " + column + ", Integer value expected, found " + v.getClass());
                                      throw new IllegalArgumentException("On column " + column + ", Integer value expected, found " + v.getClass());
                                    }
                                    break;
                                case Types.NUMERIC:
                                    if (v instanceof Double) {
                                      statement.setDouble(index, (Double) v);
                                    } else {
                                      LOG.severe("On column " + column + ", Double value expected, found " + v.getClass());
                                      throw new IllegalArgumentException("On column " + column + ", Double value expected, found " + v.getClass());
                                    }
                                    break;
                                default:
                                    throw new IllegalArgumentException("Unknown SQL type code " + sqlTypeCode + " on column " + column);
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
     * @param columnsDef Properties defining the columns
     * @return the cell processors
     */
    private static CellProcessor[] getProcessors(Properties columnsDef, String[] csvHeader) {

        String columnsStr = columnsDef.getProperty("__COLUMNS");
        List<String> columns = Arrays.asList(StringUtils.split(columnsStr, ","));
        List<String> csvColumns = Arrays.asList(csvHeader);

        List<String> diff2csv = new ArrayList<>(columns);
        diff2csv.removeAll(csvColumns);

        List<String> diff2table = new ArrayList<>(csvColumns);
        diff2table.removeAll(columns);

        if (!diff2csv.isEmpty()) {
            for (String column: diff2csv) {
                LOG.warning("Column " + column + " is defined in the table but not in CSV file");
            }
        }

        if (!diff2table.isEmpty()) {
            throw new IllegalArgumentException("Mismatch between CSV file headers and list of columns in the table. The following columns do not exist in the table: " +
                    StringUtils.join(diff2table, ','));
        }

        // Use the list of columns from the CSV header as the source of truth, this is what will be used by SuperCSV

        return csvColumns.stream().map(column -> {
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
                    case "text":
                        return new Optional();
                    case "numeric":
                        return new Optional(new ParseDouble());
                    case "int":
                    case "integer":
                        return new Optional(new ParseInt());
                    case "date":
                        return new Optional(new ParseDate(DateTimeFormatter.ISO_DATE.toString()));
                    case "timestamp":
                        return new Optional(new ParseDate(DateTimeFormatter.ISO_DATE_TIME.toString()));
                    default:
                        throw new IllegalArgumentException("Unknown type " + colType + " on column " + column);
                }
            }

        }).toArray(CellProcessor[]::new);
    }

    private static int getSqlType(String sqlType) {
        switch (shortType(sqlType)) {
            case "char":
                return Types.CHAR;
            case "varchar":
                return Types.VARCHAR;
            case "text":
                return Types.CLOB;
            case "int":
            case "integer":
                return Types.INTEGER;
            case "numeric":
                return Types.NUMERIC;
            case "date":
                return Types.DATE;
            case "timestamp":
                return Types.TIMESTAMP;
            default:
                throw new IllegalArgumentException("Unknown SQL type: " + sqlType);
        }
    }

    private static String shortType(String sqlType) {
        return sqlType.replaceAll("\\(.*\\)", "").toLowerCase();
    }

    @Override
    public Integer getChecksum() {
        int checksum = 0;
        for (String dataset: getDatasets()) {
            checksum += computeChecksum(dataset);
        }
        return checksum;
    }

    private int computeChecksum(String dataset) {
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
            LOG.log(Level.WARNING, "Cannot read data from " + dataset + "_dataset.properties", e);
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
}
