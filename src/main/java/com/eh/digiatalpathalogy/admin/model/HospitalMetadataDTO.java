package com.eh.digiatalpathalogy.admin.model;

import java.util.List;

public class HospitalMetadataDTO {

    private List<String> locations;
    private List<String> names;

    public HospitalMetadataDTO(List<String> locations, List<String> names) {
        this.locations = locations;
        this.names = names;
    }

    public HospitalMetadataDTO() {
    }

    public List<String> getLocations() {
        return locations;
    }

    public void setLocations(List<String> locations) {
        this.locations = locations;
    }

    public List<String> getNames() {
        return names;
    }

    public void setNames(List<String> names) {
        this.names = names;
    }
}
