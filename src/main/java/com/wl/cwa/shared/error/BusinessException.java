package com.wl.cwa.shared.error;

import java.util.List;

/** Thrown by application services to signal a client-visible business failure. */
public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String msgOverride;
    private final List<FieldErrorDto> fieldErrors;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, null, null, List.of());
    }

    public BusinessException(ErrorCode errorCode, String detail) {
        this(errorCode, detail, null, List.of());
    }

    public BusinessException(ErrorCode errorCode, String detail, String msgOverride, List<FieldErrorDto> fieldErrors) {
        super(detail != null ? detail : errorCode.defaultMsg());
        this.errorCode = errorCode;
        this.msgOverride = msgOverride;
        this.fieldErrors = fieldErrors == null ? List.of() : List.copyOf(fieldErrors);
    }

    public ErrorCode errorCode() {
        return errorCode;
    }

    public String msgOverride() {
        return msgOverride;
    }

    public List<FieldErrorDto> fieldErrors() {
        return fieldErrors;
    }
}
