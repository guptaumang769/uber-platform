package com.umang.uber.trip.service;

import com.umang.uber.common.enums.TripStatus;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

/**
 * The single source of truth for legal trip transitions, kept pure so it is fully unit-testable.
 *
 * <p>Happy path: MATCHED -> EN_ROUTE -> ONGOING -> COMPLETED. A trip may be CANCELLED from any of
 * the early, pre-ONGOING states (MATCHED, EN_ROUTE). Terminal states (COMPLETED, CANCELLED) and
 * an in-progress ONGOING trip cannot be cancelled, and no state may skip a step. Any transition
 * not in this table is rejected — this prevents illegal jumps like COMPLETED -> ONGOING.
 */
public final class TripStateMachine {

    private static final Map<TripStatus, Set<TripStatus>> ALLOWED = Map.of(
            TripStatus.MATCHED, EnumSet.of(TripStatus.EN_ROUTE, TripStatus.CANCELLED),
            TripStatus.EN_ROUTE, EnumSet.of(TripStatus.ONGOING, TripStatus.CANCELLED),
            TripStatus.ONGOING, EnumSet.of(TripStatus.COMPLETED),
            TripStatus.COMPLETED, EnumSet.noneOf(TripStatus.class),
            TripStatus.CANCELLED, EnumSet.noneOf(TripStatus.class),
            TripStatus.REQUESTED, EnumSet.of(TripStatus.MATCHED));

    private TripStateMachine() {
    }

    public static boolean canTransition(TripStatus from, TripStatus to) {
        return ALLOWED.getOrDefault(from, EnumSet.noneOf(TripStatus.class)).contains(to);
    }

    /** Validate a transition or throw. Callers use this before mutating a Trip. */
    public static void assertCanTransition(TripStatus from, TripStatus to) {
        if (!canTransition(from, to)) {
            throw new IllegalStateException(
                    "Illegal trip transition " + from + " -> " + to);
        }
    }
}
