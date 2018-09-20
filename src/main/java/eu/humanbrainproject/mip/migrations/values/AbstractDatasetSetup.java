package eu.humanbrainproject.mip.migrations.values;

import eu.humanbrainproject.mip.migrations.MipMigration;
import eu.humanbrainproject.mip.migrations.datapackage.Field;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.MigrationChecksumProvider;
import org.flywaydb.core.api.migration.MigrationInfoProvider;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.supercsv.cellprocessor.*;
import org.supercsv.cellprocessor.constraint.UniqueHashCode;
import org.supercsv.cellprocessor.ift.CellProcessor;
import org.supercsv.io.CsvListReader;
import org.supercsv.io.ICsvListReader;
import org.supercsv.prefs.CsvPreference;

import java.io.FileReader;
import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Types;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

public abstract class AbstractDatasetSetup extends MipMigration implements JdbcMigration, MigrationInfoProvider, MigrationChecksumProvider {

    private static final String SQL_INSERT = "INSERT INTO ${table}(${keys}) VALUES(${values})";
    private static final String TABLE_REGEX = "\\$\\{table}";
    private static final String KEYS_REGEX = "\\$\\{keys}";
    private static final String VALUES_REGEX = "\\$\\{values}";
    private static final int BATCH_SIZE = 1000;

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
        String columnsStr = columnsDef.getProperty("__COLUMNS");
        List<String> columns = Arrays.asList(StringUtils.split(columnsStr, ","));

        if (columnsDef.getProperty(column + ".constraints", "").equals("is_index"))
        final String tableName = getDatasetTableName(datasetName);

        return dataset.getProperty("__DELETE_SQL", "DELETE FROM " + tableName)
                .replaceFirst(TABLE_REGEX, tableName);
    }

    private void loadDataset(Connection connection, String datasetName) throws IOException, SQLException {
        final String csvFileName = getDatasetCsvFilePath(datasetName);
        final String tableName = getDatasetTableName(datasetName);

        if (csvFileName.equals("/dev/null") || csvFileName.equals("/data/")) {
            getLogger().warning("No data will be loaded in dataset " + datasetName);
            return;
        }

        final String deleteSql = getDatasetDeleteQuery(datasetName);
        final String primaryKey = getDatasetPrimaryKey(datasetName);
        final Properties columns = getColumnsProperties(tableName);

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
                            getLogger().warning("Column type for " + column + " is not defined in columns.properties");
                        }
                        final int sqlTypeCode = getSqlType(sqlType);
                        if (v == null) {
                            statement.setNull(index, sqlTypeCode);
                        } else {
                            switch (sqlTypeCode) {
                                case Types.CHAR:
                                case Types.VARCHAR:
                                case Types.CLOB:
                                    if (v instanceof String) {
                                        statement.setString(index, (String) v);
                                    } else {
                                        getLogger().severe("On column " + column + ", String value expected, found " + v.getClass());
                                        throw new IllegalArgumentException("On column " + column + ", String value expected, found " + v.getClass());
                                    }
                                    break;
                                case Types.INTEGER:
                                    if (v instanceof Integer) {
                                        statement.setInt(index, (Integer) v);
                                    } else {
                                        getLogger().severe("On column " + column + ", Integer value expected, found " + v.getClass());
                                        throw new IllegalArgumentException("On column " + column + ", Integer value expected, found " + v.getClass());
                                    }
                                    break;
                                case Types.NUMERIC:
                                    if (v instanceof Double) {
                                        statement.setDouble(index, (Double) v);
                                    } else {
                                        getLogger().severe("On column " + column + ", Double value expected, found " + v.getClass());
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

    /**
     * Sets up the processors used for the examples. There are 10 CSV columns, so 10 processors are defined. Empty
     * columns are read as null (hence the NotNull() for mandatory columns).
     *
     * @param columnsDef Properties defining the columns
     * @return the cell processors
     */
    private CellProcessor[] getProcessors(List<Field> columnsDef, String primaryKey, String[] csvHeader) {

        List<String> csvColumns = Arrays.asList(csvHeader);
        List<String> definedColumns = columnsDef.stream().map(Field::getName).collect(Collectors.toList());
        List<String> diff2csv = new ArrayList<>(definedColumns);
        diff2csv.removeAll(csvColumns);

        List<String> diff2table = new ArrayList<>(csvColumns);
        diff2table.removeAll(definedColumns);

        if (!diff2csv.isEmpty()) {
            for (String column : diff2csv) {
                getLogger().warning("Column " + column + " is defined in the table but not in CSV file");
            }
        }

        if (!diff2table.isEmpty()) {
            throw new IllegalArgumentException("Mismatch between CSV file headers and list of columns in the table. The following columns do not exist in the table: " +
                    StringUtils.join(diff2table, ','));
        }

        // Use the list of columns from the CSV header as the source of truth, this is what will be used by SuperCSV

        return columnsDef.stream()
                .filter(col -> csvColumns.contains(col.getName()))
                .map(column -> getCellProcessorAdaptor(primaryKey, column))
                .toArray(CellProcessor[]::new);
    }

    private CellProcessorAdaptor getCellProcessorAdaptor(String primaryKey, Field column) {
        String colType = shortType(column.getType());
        if (column.getName().equals(primaryKey)) {
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
                case "number":
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
            case "number":
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

}
