package io.github.henriquevmdev.erpconnector.connector.fake;

import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * An in-memory "ERP" with its own record format and its own failures, so {@link FakeConnector} has real mapping work
 * to do, like a connector for a real ERP. Immutable: build a new one per test.
 */
public final class FakeErp {

    /** The fake ERP's record. Fields are nullable, as in a foreign system's payload. */
    public record Row(String id, Instant changedAt, String customerDocument, String customerEmail,
                      String customerPhone, String channel) {
    }

    /** Ways the fake ERP fails. {@link #CORRUPT_ROW} is not thrown: it means rows come back with no change date. */
    public enum Problem { REJECT_CREDENTIALS, TOO_MANY_REQUESTS, UNAVAILABLE, BAD_REQUEST, CORRUPT_ROW }

    /** The fake ERP's own error, which the connector must translate. */
    public static final class ErpFailure extends Exception {
        private final Problem problem;
        private final Duration retryAfter;

        ErpFailure(Problem problem, Duration retryAfter) {
            super("fake ERP error: " + problem);
            this.problem = problem;
            this.retryAfter = retryAfter;
        }

        public Problem problem() {
            return problem;
        }

        public Duration retryAfter() {
            return retryAfter;
        }
    }

    private static final Duration RETRY_AFTER = Duration.ofSeconds(2);

    private final List<Row> rows;
    private final Problem problem;

    private FakeErp(List<Row> rows, Problem problem) {
        this.rows = rows.stream().sorted(Comparator.comparing(Row::id)).toList();
        this.problem = problem;
    }

    public static FakeErp holding(List<Row> rows) {
        return new FakeErp(rows, null);
    }

    public static FakeErp failing(Problem problem, List<Row> rows) {
        return new FakeErp(rows, Objects.requireNonNull(problem, "problem"));
    }

    void ping() throws ErpFailure {
        failIfBroken();
    }

    /** Rows changed in {@code [from, to)}, sorted by id, from {@code offset}, at most {@code limit}. */
    List<Row> query(Instant from, Instant to, int offset, int limit) throws ErpFailure {
        failIfBroken();
        return rows.stream()
                .map(this::corruptIfAsked)
                .filter(r -> r.changedAt() == null || !r.changedAt().isBefore(from) && r.changedAt().isBefore(to))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    Optional<Row> find(String id) throws ErpFailure {
        failIfBroken();
        return rows.stream().filter(r -> r.id().equals(id)).map(this::corruptIfAsked).findFirst();
    }

    private void failIfBroken() throws ErpFailure {
        if (problem != null && problem != Problem.CORRUPT_ROW) {
            throw new ErpFailure(problem, problem == Problem.TOO_MANY_REQUESTS ? RETRY_AFTER : null);
        }
    }

    private Row corruptIfAsked(Row row) {
        if (problem != Problem.CORRUPT_ROW) {
            return row;
        }
        return new Row(row.id(), null, row.customerDocument(), row.customerEmail(), row.customerPhone(), row.channel());
    }
}
