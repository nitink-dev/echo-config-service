package com.eh.digiatalpathalogy.admin.exception;


public class InvalidScanProgressException extends RuntimeException {

    public InvalidScanProgressException(String message) {
        super(message);
    }

    public InvalidScanProgressException(String slideBarcode, String reason) {
        super(String.format("Invalid scan progress for slide [%s]: %s", slideBarcode, reason));
    }
}