package com.eh.digiatalpathalogy.admin.exception;

import org.apache.catalina.connector.ClientAbortException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.ldap.CommunicationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.bind.support.WebExchangeBindException;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import org.springframework.web.server.ServerWebInputException;
import reactor.core.Exceptions;

import java.io.IOException;
import java.net.ConnectException;
import java.util.LinkedHashMap;
import java.util.Map;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleGenericException(Exception ex) {
        log.error("Unhandled exception: {}", ex.getMessage(), ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(), "An unexpected error occurred."));
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ResponseEntity<Map<String, Object>> handleResourceNotFound(ResourceNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(InternalServerException.class)
    public ResponseEntity<Map<String, Object>> handleInternalServerException(InternalServerException ex) {
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(buildErrorResponse(ex.getErrorCode(), ex.getMessage()));
    }

    @ExceptionHandler(HttpRequestException.class)
    public ResponseEntity<Map<String, Object>> handleHttpRequestException(HttpRequestException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatus().value());
        if (status == null) {
            status = HttpStatus.INTERNAL_SERVER_ERROR;
        }
        return ResponseEntity.status(status)
                .body(buildErrorResponse(status.getReasonPhrase(), ex.getResponseBody()));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParam(NoResourceFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(buildErrorResponse(HttpStatus.NOT_FOUND.getReasonPhrase(), ex.getMessage()));
    }

    @ExceptionHandler(WebExchangeBindException.class)
    public ResponseEntity<Map<String, Object>> handleWebFluxBodyValidation(WebExchangeBindException ex) {
        Map<String, String> fieldErrors = new LinkedHashMap<>();
        ex.getFieldErrors().forEach(err ->
                fieldErrors.put(err.getField(), err.getDefaultMessage())
        );

        Map<String, Object> errorBody = buildErrorResponse("Validation Error",
                "Validation failed for one or more fields.");
        errorBody.put("details", fieldErrors);

        return ResponseEntity.badRequest().body(errorBody);
    }

    @ExceptionHandler(ServerWebInputException.class)
    public ResponseEntity<Map<String, Object>> handleServerWebInput(ServerWebInputException ex) {
        Map<String, Object> body = buildErrorResponse(
                "Bad Request",
                ex.getReason() != null ? ex.getReason() : "Invalid request payload."
        );
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler({ClientAbortException.class, IOException.class})
    public ResponseEntity<Map<String, Object>> handleClientAbort(Exception ex) {
        if (isClientAbort(ex)) {
            return ResponseEntity.status(HttpStatus.NO_CONTENT).build();
        }
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(buildErrorResponse(HttpStatus.INTERNAL_SERVER_ERROR.getReasonPhrase(),
                        "An unexpected error occurred."));
    }

    private boolean isClientAbort(Throwable ex) {
        Throwable root = Exceptions.unwrap(ex);
        String hostErrorConnectionMsg = "An established connection was aborted by the software in your host machine";
        String clientAbortMsg = "Connection reset by peer";
        return root instanceof IOException ||
                root.getClass().getName().contains("ClientAbortException") ||
                (root.getMessage() != null &&
                        (root.getMessage().contains("Broken pipe")
                                || root.getMessage().contains(clientAbortMsg))
                        || root.getMessage().contains(hostErrorConnectionMsg));
    }

    private Map<String, Object> buildErrorResponse(String errorCode, String errorMessage) {
        Map<String, Object> errorBody = new LinkedHashMap<>();
        errorBody.put("errorCode", errorCode != null ? errorCode : "Unknown Error");
        errorBody.put("errorDescription", errorMessage != null ? errorMessage : "An unexpected error occurred.");
        return errorBody;
    }

    /**
     * Spring Security access denied (403)
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<Map<String, Object>> handleAccessDenied(AccessDeniedException ex) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(buildErrorResponse(HttpStatus.FORBIDDEN.getReasonPhrase(),
                        "Insufficient permissions"));
    }

    @ExceptionHandler(InternalAuthenticationServiceException.class)
    public ResponseEntity<Map<String, Object>> handleInternalAuthException(InternalAuthenticationServiceException ex) {

        Throwable root = ex.getCause();
        String errorCode = "Authentication Failed";
        String userMessage = "Authentication failed. Please try again.";
        String message = (root != null ? root.getMessage() : ex.getMessage());
        message = message != null ? message : "";

        if (root instanceof ConnectException || root instanceof CommunicationException) {
            log.error("LDAP connectivity failure: unable to reach the directory server. Cause: {}", root.getMessage(), root);
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(buildErrorResponse("Auth Service Unavailable", "Authentication service is temporarily unavailable. Please try again later"));
        }
        if (message.contains("52e")) {
            errorCode = "Invalid Credentials";
            userMessage = "The username or password is incorrect.";
        }
        if (message.contains("775")) {
            errorCode = "Account Locked";
            userMessage = "Your account is locked. Please contact the administrator.";
        }
        if (message.contains("533")) {
            errorCode = "Account Disabled";
            userMessage = "Your account is disabled.";
        }
        if (message.contains("532")) {
            errorCode = "Password Expired";
            userMessage = "Your password has expired. Please reset it.";
        }
        if (message.contains("701")) {
            errorCode = "Account Expired";
            userMessage = "Your account has expired.";
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildErrorResponse(errorCode, userMessage));
    }


    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleAuthenticationException(AuthenticationException ex) {

        if (ex instanceof BadCredentialsException) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(buildErrorResponse("Invalid Credentials", "The username or password is incorrect."));
        }

        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildErrorResponse("Authentication Failed", "The username or password is incorrect."));
    }

    @ExceptionHandler(org.springframework.ldap.AuthenticationException.class)
    public ResponseEntity<Map<String, Object>> handleLdapAuthenticationException(org.springframework.ldap.AuthenticationException ex) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .body(buildErrorResponse("Authentication Failed", "The username or password is incorrect."));
    }

}
