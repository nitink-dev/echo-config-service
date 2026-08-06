package com.eh.digiatalpathalogy.admin.exception;

import org.springframework.http.HttpStatusCode;

public class HttpRequestException extends RuntimeException {

    private final HttpStatusCode status;
    private final String responseBody;

    public HttpRequestException(HttpStatusCode status, String responseBody) {
        super("HTTP request failed with status " + status + ": " + responseBody);
        this.status = status;
        this.responseBody = responseBody;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public HttpStatusCode getStatus() {
        return status;
    }
}
