package com.languageapp.backend.enums;

/**
 * Represents the lifecycle states of a head-to-head Challenge.
 */
public enum ChallengeStatus {
    /** The challenge is created, and the challenger is currently playing the lesson. Invisible to the opponent. */
    DRAFT,

    /** The challenger has finished. The challenge is now visible to the opponent, awaiting their attempt. */
    PENDING,

    /** Both users have completed the lesson. The evaluation engine has determined the winner. */
    COMPLETED,

    /** The opponent explicitly rejected the challenge. Automatically results in a win for the challenger. */
    DECLINED,

    /** The opponent failed to complete the challenge within the specified timeframe. */
    EXPIRED
}