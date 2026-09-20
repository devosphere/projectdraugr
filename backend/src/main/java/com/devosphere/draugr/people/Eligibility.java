package com.devosphere.draugr.people;

import org.springframework.jdbc.core.JdbcTemplate;

import java.util.UUID;

/**
 * What a people's own classification permits (#110, V344).
 *
 * <p>V344 gave every species sixteen columns saying what kind of being it is, including four that name exactly which
 * social acts are meaningful toward it: {@code trade_eligible}, {@code agreement_eligible},
 * {@code companionship_eligible} and {@code community_membership}. Being classified PEOPLE was enough to reach every
 * one of those acts, and the columns were read by nothing — a declaration the code ignored, which is this project's
 * most common defect and the reason this class exists.
 *
 * <p>Now each act asks. A people who keep no bargains with outsiders, or who never let one of their own walk away
 * with a stranger, refuse at the door and say so in their own terms — and a future people can be given a different
 * social life by data alone, which is what the classification was for.
 */
public final class Eligibility {

    private Eligibility() { }

    public static final String TRADE = "trade_eligible";
    public static final String AGREEMENT = "agreement_eligible";
    public static final String COMPANIONSHIP = "companionship_eligible";
    public static final String MEMBERSHIP = "community_membership";
    public static final String WORKING = "working_relationship_eligible";

    /** The first refusal there is, of the ones asked for, or null when none of them refuses. */
    public static String firstOf(String... refusals) {
        for (String refusal : refusals) if (refusal != null) return refusal;
        return null;
    }

    /** The refusal when this community's species is not classified for that act, or null when it is. */
    public static String refusal(JdbcTemplate jdbc, UUID community, String column, String refusal) {
        Boolean allowed = jdbc.query(
            "SELECT p." + column + " FROM native_community n JOIN cognition_profile p ON p.species_key=n.species_key WHERE n.id=?",
            rs -> rs.next() ? rs.getBoolean(1) : Boolean.TRUE, community);
        return Boolean.TRUE.equals(allowed) ? null : refusal;
    }
}
