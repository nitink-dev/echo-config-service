package com.eh.digiatalpathalogy.admin.model;

import java.util.List;

public class AppConfiguration {

    private String name;
    private List<String> profiles;
    private String label;
    private String version;
    private String state;
    private List<ConfigPropertySource> propertySources;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public List<String> getProfiles() {
        return profiles;
    }

    public void setProfiles(List<String> profiles) {
        this.profiles = profiles;
    }

    public String getLabel() {
        return label;
    }

    public void setLabel(String label) {
        this.label = label;
    }

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public String getState() {
        return state;
    }

    public void setState(String state) {
        this.state = state;
    }

    public List<ConfigPropertySource> getPropertySources() {
        return propertySources;
    }

    public void setPropertySources(List<ConfigPropertySource> propertySources) {
        this.propertySources = propertySources;
    }
}
