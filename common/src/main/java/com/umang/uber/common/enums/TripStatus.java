package com.umang.uber.common.enums;

/** Trip lifecycle states. See trip-service's state machine for the legal transitions. */
public enum TripStatus {
    REQUESTED,
    MATCHED,
    EN_ROUTE,
    ONGOING,
    COMPLETED,
    CANCELLED
}
