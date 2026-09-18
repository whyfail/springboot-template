package com.wl.cwa.audit.domain;

/** Outcome of an audited event; values are constrained by the database CHECK. */
public enum AuditResult {
    SUCCESS,
    FAILURE,
    DENIED
}
