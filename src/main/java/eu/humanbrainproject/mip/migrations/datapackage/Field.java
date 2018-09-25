package eu.humanbrainproject.mip.migrations.datapackage;

import java.sql.Types;

@SuppressWarnings("unused")
public class Field {

    private String name = "";
    private String type = "";
    private String format = "default";
    private String title = "";
    private String description = "";
    private String sqlType = null;
    private Constraints constraints = new Constraints();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getType() {
        return type;
    }

    public void setType(String type) {
        this.type = type;
    }

    public String getFormat() {
        return format;
    }

    public void setFormat(String format) {
        this.format = format;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getSqlType() {
        if (sqlType == null) {
            sqlType = inferSqlType();
        }
        return sqlType;
    }

    public void setSqlType(String sqlType) {
        this.sqlType = sqlType;
    }

    private String inferSqlType() {
        switch (type) {
            case "string":
                return "text";
            case "varchar":
                return "varchar";
            case "text":
                return "text";
            case "numeric":
            case "number":
                return "numeric";
            case "int":
            case "integer":
                return "int";
            default:
                throw new IllegalArgumentException("Cannot infer SQL type from type " + type);
        }
    }

    public String getSqlTypeShort() {
        return shortType(getSqlType());
    }

    public int getSqlTypeCode() {
        switch (getSqlTypeShort()) {
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

    public Constraints getConstraints() {
        return constraints;
    }

    public void setConstraints(Constraints constraints) {
        this.constraints = constraints;
    }

    @Override
    public String toString() {
        return "Field{" +
                "name='" + name + '\'' +
                ", type='" + type + '\'' +
                ", format='" + format + '\'' +
                ", title='" + title + '\'' +
                ", description='" + description + '\'' +
                ", sqlType='" + sqlType + '\'' +
                '}';
    }

    private static String shortType(String sqlType) {
        return sqlType.replaceAll("\\(.*\\)", "").toLowerCase();
    }

}
