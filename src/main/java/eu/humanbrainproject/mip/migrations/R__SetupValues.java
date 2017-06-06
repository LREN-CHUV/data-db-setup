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
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.Types;
import java.util.*;
import java.util.stream.Collectors;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

@SuppressWarnings("unused")
public class R__SetupValues implements JdbcMigration, MigrationChecksumProvider {

    private static final String SQL_INSERT = "INSERT INTO ${table}(${keys}) VALUES(${values})";
    private static final String TABLE_REGEX = "\\$\\{table\\}";
    private static final String KEYS_REGEX = "\\$\\{keys\\}";
    private static final String VALUES_REGEX = "\\$\\{values\\}";
    private static final int BATCH_SIZE = 1000;

    public void migrate(Connection connection) throws Exception {
        try {

            Properties columns = new Properties();
            columns.load(getClass().getResourceAsStream("columns.properties"));
            final String tableName = columns.getProperty("__TABLE", "data");
            final String csvFileName = columns.getProperty("__CSV_FILE", "/data/values.csv");
            final String deleteSql = columns.getProperty("__DELETE_SQL", "DELETE FROM " + tableName);

            try (ICsvListReader csvReader = new CsvListReader(new FileReader(csvFileName), CsvPreference.STANDARD_PREFERENCE)) {

                // skip the header
                final String[] header = csvReader.getHeader(true);
                final CellProcessor[] processors = getProcessors(columns);

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
                            String sqlType = columns.getProperty(header[index - 1] + ".type", "VARCHAR");
                            if (v == null) {
                                statement.setNull(index, getSqlType(sqlType));
                            } else {
                                switch (getSqlType(sqlType)) {
                                    case Types.CHAR:
                                    case Types.VARCHAR:
                                        statement.setString(index, (String) v);
                                        break;
                                    case Types.INTEGER:
                                        statement.setInt(index, (Integer) v);
                                        break;
                                    case Types.NUMERIC:
                                        statement.setDouble(index, (Double) v);
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

            connection.commit();
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    /**
     * Sets up the processors used for the examples. There are 10 CSV columns, so 10 processors are defined. Empty
     * columns are read as null (hence the NotNull() for mandatory columns).
     *
     * @param columnsDef
     * @return the cell processors
     */
    private static CellProcessor[] getProcessors(Properties columnsDef) {

        String columnsStr = columnsDef.getProperty("__COLUMNS");
        List<String> columns = Arrays.asList(StringUtils.split(columnsStr, ","));

        List<CellProcessor> processors = columns.stream().map(column -> {
            if (columnsDef.getProperty(column + ".constraints", "").equals("is_index")) {
                return new UniqueHashCode();
            } else {
                String colType = shortType(columnsDef.getProperty(column + ".type", "?"));
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

    private int getSqlType(String sqlType) {
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
            final String csvFileName = columns.getProperty("__CSV_FILE", "/data/values.csv");
            checksum = computeChecksum(csvFileName);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return checksum;
    }

    private static int computeChecksum(String filepath) {
        final int BUFFER_SIZE = 2048;
        int checksum = 0;
        try {
            MessageDigest md = MessageDigest.getInstance("MD5");  // MD5 or SHA-1 or SHA-256
            InputStream is = new FileInputStream(filepath);
            byte[] bytes = new byte[BUFFER_SIZE];
            int numBytes;
            while ((numBytes = is.read(bytes)) != -1) {
                md.update(bytes, 0, numBytes);
            }
            byte[] digest = md.digest();
            for (Byte b: digest) {
                checksum += b.intValue();
            }
        } catch (NoSuchAlgorithmException | IOException e) {
            e.printStackTrace();
        }
        return checksum;
    }

}
