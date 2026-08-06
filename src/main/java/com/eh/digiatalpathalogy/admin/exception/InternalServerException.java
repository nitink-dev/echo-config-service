package com.eh.digiatalpathalogy.admin.exception;

public class InternalServerException extends RuntimeException {

    private static final String DEFAULT_ERROR_CODE = "Internal Server Error";
    private final String errorCode;

    public InternalServerException(String message) {
        super(message);
        this.errorCode = DEFAULT_ERROR_CODE;
    }

    public InternalServerException(String message, Throwable cause) {
        super(message, cause);
        this.errorCode = DEFAULT_ERROR_CODE;
    }

    public String getErrorCode() {
        return errorCode;
    }

}

