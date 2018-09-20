package eu.humanbrainproject.mip.migrations.datapackage;

import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

public class Datapackage {

    private String name;
    private String description;
    private String title;
    private String schema;

    @JsonProperty("resources")
    private List<Resource> resources;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getSchema() {
        return schema;
    }

    public void setSchema(String schema) {
        this.schema = schema;
    }

    public List<Resource> getResources() {
        return resources;
    }

    public void setResources(List<Resource> resources) {
        this.resources = resources;
    }

    public Resource getResource(String datasetName) {
        for (Resource r: resources) {
            if (r.getName().equals(datasetName)) {
                return r;
            }
        }
        throw new IllegalArgumentException("Invalid resource " + datasetName);
    }
}
