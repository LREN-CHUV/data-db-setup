package eu.humanbrainproject.mip.migrations.values;

import eu.humanbrainproject.mip.migrations.MigrationConfiguration;
import eu.humanbrainproject.mip.migrations.datapackage.Field;
import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.MigrationChecksumProvider;
import org.flywaydb.core.api.migration.MigrationInfoProvider;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import org.slf4j.LoggerFactory;
import org.supercsv.cellprocessor.*;
import org.supercsv.cellprocessor.Optional;
import org.supercsv.cellprocessor.constraint.NotNull;
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
import java.util.*;
import java.util.stream.Collectors;
import java.util.zip.CRC32;

import org.slf4j.Logger;

@SuppressWarnings("unused")
public class R__SetupValues implements JdbcMigration, MigrationInfoProvider, MigrationChecksumProvider {

    private static final Logger LOG = LoggerFactory.getLogger("Setup data-package");

    private static final String TABLE_REGEX = "\\$\\{table}";
    private static final String KEYS_REGEX = "\\$\\{keys}";
    private static final String VALUES_REGEX = "\\$\\{values}";
    private static final String SQL_INSERT = "INSERT INTO \"${table}\"(${keys}) VALUES(${values})";
    private static final int BATCH_SIZE = 100;

    protected final MigrationConfiguration config = new MigrationConfiguration();

    @Override
    public boolean isUndo() {
        return false;
    }

    @Override
    public String getDescription() {
        String[] datasets = config.getDatasets();
        return "Setup dataset" + (datasets.length > 1 ? "s " : " ") + StringUtils.join(datasets, ',');
    }


    @Override
    public void migrate(Connection connection) throws Exception {
        String[] datasets = config.getDatasets();

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
            LOG.error("Cannot migrate data", e);
            if (e.getNextException() != null) {
                LOG.error("Caused by", e.getNextException());
            }
        } catch (Exception e) {
            LOG.error("Cannot migrate data", e);
            if (e.getCause() != null) {
                LOG.error("Caused by", e.getCause());
            }
            throw e;
        }
    }

    private void loadDataset(Connection connection, String datasetName) throws IOException, SQLException {
        final MigrationConfiguration.DatasetConfiguration datasetConfiguration = config.getDatasetConfiguration(datasetName);
        final String csvFileName = config.getDataResourcePath(datasetConfiguration.getDatasetCsvFilePath());
        final String tableName = datasetConfiguration.getDatasetTableName();

        if (csvFileName.equals("/dev/null") || csvFileName.equals("/data/")) {
            LOG.warn("No data will be loaded in dataset " + datasetName);
            return;
        }

        final String deleteSql = datasetConfiguration.getDatasetDeleteQuery();
        final String primaryKey = datasetConfiguration.getDatasetPrimaryKey();
        final List<Field> fields = datasetConfiguration.getFields();

        try (ICsvListReader csvReader = new CsvListReader(new FileReader(csvFileName), CsvPreference.STANDARD_PREFERENCE)) {

            // skip the header
            final String[] header = csvReader.getHeader(true);
            final CellProcessor[] processors = getProcessors(fields, primaryKey, header);

            String questionMarks = StringUtils.repeat("?,", header.length);
            questionMarks = (String) questionMarks.subSequence(0, questionMarks
                    .length() - 1);

            String query = SQL_INSERT.replaceFirst(TABLE_REGEX, tableName);
            query = query.replaceFirst(KEYS_REGEX, "\"" + StringUtils.join(header, "\",\"") + "\"");
            query = query.replaceFirst(VALUES_REGEX, questionMarks);

            try (PreparedStatement statement = connection.prepareStatement(query)) {

                // Delete data from table before loading csv
                LOG.info("Deleting previous records using query: " + deleteSql);
                connection.createStatement().execute(deleteSql);

                List<Object> values;
                while ((values = csvReader.read(processors)) != null) {

                    int index = 1;
                    for (Object v : values) {
                        String column = header[index - 1];
                        Field field = fields.stream()
                                .filter(f -> f.getName().equals(column))
                                .findFirst()
                                .orElseThrow(() -> new IllegalStateException("Expected to find column " + column));
                        final int sqlTypeCode = field.getSqlTypeCode();

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
                                        LOG.error("On column " + column + ", String value expected, found " + v.getClass());
                                        throw new IllegalArgumentException("On column " + column + ", String value expected, found " + v.getClass());
                                    }
                                    break;
                                case Types.INTEGER:
                                    if (v instanceof Integer) {
                                        statement.setInt(index, (Integer) v);
                                    } else {
                                        LOG.error("On column " + column + ", Integer value expected, found " + v.getClass());
                                        throw new IllegalArgumentException("On column " + column + ", Integer value expected, found " + v.getClass());
                                    }
                                    break;
                                case Types.NUMERIC:
                                    if (v instanceof Double) {
                                        statement.setDouble(index, (Double) v);
                                    } else {
                                        LOG.error("On column " + column + ", Double value expected, found " + v.getClass());
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
                        LOG.info("Saving batch records #" + csvReader.getLineNumber());
                    }
                }
                statement.executeBatch(); // insert remaining records
                LOG.info("Saved " + (csvReader.getLineNumber() - 1) + " records for dataset " + datasetName + " into the database");
            }

        }
    }

    @Override
    public Integer getChecksum() {
        int checksum = 0;
        for (String dataset : config.getDatasets()) {
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
        InputStream datasetResource = config.getDatasetResource(dataset);
        byte[] data = new byte[1024];
        int read;
        try {
            while ((read = datasetResource.read(data)) > 0) {
                crc32.update(data, 0, read);
            }
        } catch (IOException e) {
            LOG.warn("Cannot read data from dataset " + dataset, e);
        }

        return (int) crc32.getValue();
    }


    @Override
    public MigrationVersion getVersion() {
        return null;
    }

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
                LOG.warn("Column " + column + " is defined in the table but not in CSV file");
            }
        }

        if (!diff2table.isEmpty()) {
            throw new IllegalArgumentException("Mismatch between CSV file headers and list of columns in the table. The following columns do not exist in the table: " +
                    StringUtils.join(diff2table, ','));
        }

        // Use the list of columns from the CSV header as the source of truth, this is what will be used by SuperCSV

        Map<String, CellProcessor> processors = columnsDef.stream()
                .filter(col -> csvColumns.contains(col.getName()))
                .map(column -> new Pair<>(column.getName(), getCellProcessorAdaptor(primaryKey, column)))
                .collect(Collectors.toMap(Pair::getKey, Pair::getValue));

        return csvColumns.stream()
                .map(processors::get)
                .toArray(CellProcessor[]::new);
    }

    private CellProcessorAdaptor getCellProcessorAdaptor(String primaryKey, Field column) {
        String colType = column.getSqlTypeShort();
        if (column.getName().equals(primaryKey)) {
            if ("int".equals(colType) || "integer".equals(colType)) {
                return new ParseInt();
            } else {
                return new UniqueHashCode();
            }
        } else {
            switch (colType) {
                case "char":
                case "varchar":
                case "text":
                case "string":
                    if (column.getConstraints().isRequired()) {
                        LOG.debug("Read column " + column + " from CSV as required string");
                        return new NotNull();
                    } else {
                        LOG.debug("Read column " + column + " from CSV as optional string");
                        return new Optional();
                    }
                case "numeric":
                case "number":
                    if (column.getConstraints().isRequired()) {
                        LOG.debug("Read column " + column + " from CSV as required double");
                        return new ParseDouble();
                    } else {
                        LOG.debug("Read column " + column + " from CSV as optional double");
                        return new Optional(new ParseDouble());
                    }
                case "int":
                case "integer":
                    if (column.getConstraints().isRequired()) {
                        LOG.debug("Read column " + column + " from CSV as required integer");
                        return new ParseInt();
                    } else {
                        LOG.debug("Read column " + column + " from CSV as optional integer");
                        return new Optional(new ParseInt());
                    }
                case "date":
                    final ParseDate parseDate = new ParseDate(DateTimeFormatter.ISO_DATE.toString());
                    if (column.getConstraints().isRequired()) {
                        LOG.debug("Read column " + column + " from CSV as required date");
                        return parseDate;
                    } else {
                        LOG.debug("Read column " + column + " from CSV as optional date");
                        return new Optional(parseDate);
                    }
                case "timestamp":
                    final ParseDate parseTimestamp = new ParseDate(DateTimeFormatter.ISO_DATE_TIME.toString());
                    if (column.getConstraints().isRequired()) {
                        LOG.debug("Read column " + column + " from CSV as required timestamp");
                        return parseTimestamp;
                    } else {
                        LOG.debug("Read column " + column + " from CSV as optional timestamp");
                        return new Optional(parseTimestamp);
                    }
                default:
                    throw new IllegalArgumentException("Unknown type " + colType + " on column " + column);
            }
        }
    }

    private static class Pair<T> {
        private final java.lang.String key;
        private final T value;

        Pair(String key, T value) {
            this.key = key;
            this.value = value;
        }

        String getKey() {
            return key;
        }

        T getValue() {
            return value;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            Pair<?> pair = (Pair<?>) o;
            return Objects.equals(key, pair.key) &&
                    Objects.equals(value, pair.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(key);
        }
    }
}
