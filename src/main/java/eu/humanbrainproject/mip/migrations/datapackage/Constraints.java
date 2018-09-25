package eu.humanbrainproject.mip.migrations.datapackage;

import com.fasterxml.jackson.annotation.JsonProperty;

public class Constraints {

    private boolean required = false;
    private boolean unique = false;
    private String pattern;
    @JsonProperty("enum")
    private String[] enumeration;
    private Integer minLength;
    private Integer maxLength;

    public boolean isRequired() {
        return required;
    }

    public void setRequired(boolean required) {
        this.required = required;
    }

    public boolean isUnique() {
        return unique;
    }

    public void setUnique(boolean unique) {
        this.unique = unique;
    }

    public String getPattern() {
        return pattern;
    }

    public void setPattern(String pattern) {
        this.pattern = pattern;
    }

    public String[] getEnumeration() {
        return enumeration;
    }

    public void setEnumeration(String[] enumeration) {
        this.enumeration = enumeration;
    }

    public Integer getMinLength() {
        return minLength;
    }

    public void setMinLength(Integer minLength) {
        this.minLength = minLength;
    }

    public Integer getMaxLength() {
        return maxLength;
    }

    public void setMaxLength(Integer maxLength) {
        this.maxLength = maxLength;
    }
}
