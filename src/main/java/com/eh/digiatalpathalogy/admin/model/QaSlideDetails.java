package com.eh.digiatalpathalogy.admin.model;

import com.eh.digiatalpathalogy.admin.entity.QaSlide;

import java.util.List;

public class QaSlideDetails {

    private String dicomUrl;
    private List<QaSlide> qaSlides;

    public QaSlideDetails(String dicomUrl, List<QaSlide> qaSlides) {
        this.dicomUrl = dicomUrl;
        this.qaSlides = qaSlides;
    }

    public String getDicomUrl() {
        return dicomUrl;
    }

    public void setDicomUrl(String dicomUrl) {
        this.dicomUrl = dicomUrl;
    }

    public List<QaSlide> getQaSlides() {
        return qaSlides;
    }

    public void setQaSlides(List<QaSlide> qaSlides) {
        this.qaSlides = qaSlides;
    }
}
