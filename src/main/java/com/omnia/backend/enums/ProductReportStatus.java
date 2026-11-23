package com.omnia.backend.enums;

/** OPEN waits in the moderation queue; the others record what the admin decided. */
public enum ProductReportStatus {
    OPEN,
    HIDDEN,
    DISMISSED,
    WARNED
}
