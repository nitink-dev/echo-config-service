package com.eh.digiatalpathalogy.admin.model;

import java.util.Map;

public class ConfigPropertySource {

    private String name;
    private Map<String, Object> source;

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Map<String, Object> getSource() {
        return source;
    }

    public void setSource(Map<String, Object> source) {
        this.source = source;
    }
}
