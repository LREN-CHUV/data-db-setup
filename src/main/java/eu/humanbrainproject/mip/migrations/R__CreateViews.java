package eu.humanbrainproject.mip.migrations;

import org.apache.commons.lang3.StringUtils;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.migration.MigrationChecksumProvider;
import org.flywaydb.core.api.migration.MigrationInfoProvider;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import com.github.mustachejava.DefaultMustacheFactory;
import com.github.mustachejava.Mustache;
import com.github.mustachejava.MustacheFactory;

import java.io.*;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.zip.CRC32;

@SuppressWarnings("unused")
public class R__CreateViews extends MipMigration implements JdbcMigration, MigrationInfoProvider, MigrationChecksumProvider {

    private static final Logger LOG = Logger.getLogger("Create views");

    private final Map<String, Properties> viewProperties = new HashMap<>();

    public void migrate(Connection connection) throws Exception {
        String[] views = getViews();
        try {

            connection.setAutoCommit(false);

            for (String view: views) {
                createView(connection, view);
            }

            connection.commit();

        } catch (Exception e) {
            LOG.log(Level.SEVERE, "Cannot create views for data", e);
            throw e;
        }
    }

    private void createView(Connection connection, String view) throws IOException, SQLException {
        Map<String, Object> scopes = new HashMap<>();
        int i = 0;
        for (String table : getTables(view)) {
            Properties tableProperties = getColumnsProperties(table);

            String tableName = tableProperties.getProperty("__TABLE", table);
            String columns = tableProperties.getProperty("__COLUMNS");
            List<String> ids = getIdColumns(table);

            final Table templateValue = new Table(tableName, columns, StringUtils.join(ids, ','));
            scopes.put(table, templateValue);
            scopes.put("table" + (++i), templateValue);
        }

        Properties viewProperties = getViewProperties(view);
        String viewName = viewProperties.getProperty("__VIEW", view);
        String viewColumns = viewProperties.getProperty("__COLUMNS");

        scopes.put("view", new Table(viewName, viewColumns, ""));

        StringWriter writer = new StringWriter();
        MustacheFactory mf = new DefaultMustacheFactory();
        Mustache mustache = mf.compile(new InputStreamReader(getViewTemplateResource(view)), view + ".mustache.sql");
        mustache.execute(writer, scopes);
        writer.flush();

        String createViewSql = writer.toString();

        try {
            connection.createStatement().execute(createViewSql);
        } catch (SQLException e) {
            LOG.severe("Cannot execute the following SQL statement: \n" + createViewSql);
            throw e;
        }

    }

    private String[] getViews() {
        String viewsStr = System.getenv("VIEWS");
        if (viewsStr == null) {
            return new String[0];
        }
        return viewsStr.split(",");
    }

    private String[] getTables(String viewName) throws IOException {
        return getViewProperties(viewName).getProperty("__TABLES").split(",");
    }

    private Properties getViewProperties(String viewName) throws IOException {
        Properties viewProperties = this.viewProperties.get(viewName);
        if (viewProperties == null) {
            viewProperties = new Properties();
            InputStream viewResource = getViewResource(viewName);
            viewProperties.load(viewResource);
            this.viewProperties.put(viewName, viewProperties);
        }
        return viewProperties;
    }

    private InputStream getViewResource(String viewName) {
        String propertiesFile = (viewName == null) ? "view.properties" : viewName + "_view.properties";
        InputStream viewResource = getClass().getResourceAsStream(propertiesFile);
        if (viewResource == null && getViews().length == 1) {
            propertiesFile = "view.properties" ;
            viewResource = getClass().getResourceAsStream(propertiesFile);
        }
        if (viewResource == null) {
            throw new RuntimeException("Cannot load resource for view " + viewName + " from " +
                    getClass().getPackage().getName().replaceAll("\\.", "/") +
                    "/" + propertiesFile + ". Check VIEWS environment variable and contents of the jar");
        }
        return viewResource;
    }

    private InputStream getViewTemplateResource(String viewName) throws IOException {
        Properties viewProperties = getViewProperties(viewName);
        String propertiesFile = viewProperties.getProperty("__SQL_TEMPLATE");
        InputStream viewTemplateResource;

        if (propertiesFile != null) {
            viewTemplateResource = getClass().getResourceAsStream(propertiesFile);
        } else {
            propertiesFile = (viewName == null) ? "view.mustache.sql" : viewName + "_view.mustache.sql";
            viewTemplateResource = getClass().getResourceAsStream(propertiesFile);
            if (viewTemplateResource == null && getViews().length == 1) {
                propertiesFile = "view.mustache.sql";
                viewTemplateResource = getClass().getResourceAsStream(propertiesFile);
            }
        }
        if (viewTemplateResource == null) {
            throw new RuntimeException("Cannot load resource for view " + viewName + " from " +
                    getClass().getPackage().getName().replaceAll("\\.", "/") +
                    "/" + propertiesFile + ". Check VIEWS environment variable and contents of the jar");
        }
        return viewTemplateResource;
    }

    @Override
    public Integer getChecksum() {
        String[] views = getViews();
        int checksum = 0;
        for (String view: views) {
            try {
                checksum += computeChecksum(view);
            } catch (RuntimeException e) {
                LOG.log(Level.SEVERE, "Cannot compute checksum", e);
            }
        }
        return checksum;
    }

    private int computeChecksum(String view) {
        final CRC32 crc32 = new CRC32();

        // Use the name of the view
        byte[] bytes = view.getBytes();
        crc32.update(bytes, 0, bytes.length);

        // Use the values in the dataset
        try {
            InputStream viewResource = getViewResource(view);
            crcForResource(crc32, viewResource);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read data from " + view + "_view.properties", e);
        }

        try {
            InputStream viewTemplateResource = getViewTemplateResource(view);
            crcForResource(crc32, viewTemplateResource);
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read data from " + view + "_view.mustache.sql", e);
        }

        // CRC for tables
        try {
            for (String table : getTables(view)) {
                InputStream tableResource = getColumnsResource(table);
                crcForResource(crc32, tableResource);
            }
        } catch (IOException e) {
            LOG.log(Level.WARNING, "Cannot read table properties", e);
        }

        return (int) crc32.getValue();
    }

    private void crcForResource(CRC32 crc32, InputStream resource) throws IOException {
        byte[] data = new byte[1024];
        int read;
        while ((read = resource.read(data)) > 0) {
            crc32.update(data, 0, read);
        }
    }

    @Override
    public MigrationVersion getVersion() {
        return null;
    }

    @Override
    public String getDescription() {
        String[] views = getViews();
        return "Create view" + (views.length > 1 ? "s " : " ") + StringUtils.join(views, ',');
    }

    static class Table {

        private final String name;
        private final String columns;
        private final String ids;

        Table(String name, String columns, String ids) {

            this.name = name;
            this.columns = columns;
            this.ids = ids;
        }

        public String getName() {
            return name;
        }

        public String getColumns() {
            return columns;
        }

        public String getIds() {
            return ids;
        }

        public String getQualifiedColumns() {
            List<String> cols = Arrays.asList(columns.split(","));
            cols.replaceAll(s -> name + "." + s);
            return StringUtils.join(cols, ',');
        }

        public String getQualifiedColumnsNoId() {
            List<String> cols = new ArrayList<>(Arrays.asList(columns.split(",")));
            List<String> idList = Arrays.asList(ids.split(","));
            cols.removeAll(idList);
            cols.replaceAll(s -> name + "." + s);
            return StringUtils.join(cols, ',');
        }

        public String getQualifiedId() {
            List<String> cols = Arrays.asList(ids.split(","));
            cols.replaceAll(s -> name + "." + s);
            return StringUtils.join(cols, ',');
        }

    }
}
