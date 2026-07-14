package com.eh.digiatalpathalogy.admin.model;

public class HealthStatusResult {
    private String name;
    private String url;
    private String status;
    private Integer httpStatus;
    private long latencyMs;
    private String error;


    public HealthStatusResult( ) {

    }

    public HealthStatusResult(String status, String url, String name, Integer httpStatus, long latencyMs, String error ) {
        this.status = status;
        this.url = url;
        this.name = name;
        this.httpStatus = httpStatus;
        this.latencyMs = latencyMs;
        this.error = error;
    }
    public HealthStatusResult down(String name, String url, Integer httpStatus, long latencyMs, String error){
        return new HealthStatusResult( "DOWN", url, name, httpStatus, latencyMs, error );
    }

    public String getName ( ) {
        return name;
    }

    public void setName ( String name ) {
        this.name = name;
    }

    public String getUrl ( ) {
        return url;
    }

    public void setUrl ( String url ) {
        this.url = url;
    }

    public String getStatus ( ) {
        return status;
    }

    public void setStatus ( String status ) {
        this.status = status;
    }

    public Integer getHttpStatus ( ) {
        return httpStatus;
    }

    public void setHttpStatus ( Integer httpStatus ) {
        this.httpStatus = httpStatus;
    }

    public long getLatencyMs ( ) {
        return latencyMs;
    }

    public void setLatencyMs ( long latencyMs ) {
        this.latencyMs = latencyMs;
    }

    public String getError ( ) {
        return error;
    }

    public void setError ( String error ) {
        this.error = error;
    }
}
