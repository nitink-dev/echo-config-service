package com.eh.digiatalpathalogy.admin.exception;

public class ResourceNotFoundException extends RuntimeException {

    private static final String DEFAULT_ERROR_CODE = "Resource Not Found";
    private final String errorCode;

    public ResourceNotFoundException(String message) {
        super(message);
        this.errorCode = DEFAULT_ERROR_CODE;
    }

    public ResourceNotFoundException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = DEFAULT_ERROR_CODE;
    }

    public String getErrorCode() {
        return errorCode;
    }

}
