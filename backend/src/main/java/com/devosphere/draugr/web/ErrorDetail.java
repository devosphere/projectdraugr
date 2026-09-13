package com.devosphere.draugr.web;

import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.InvalidResultSetAccessException;
import org.springframework.jdbc.UncategorizedSQLException;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * What a developer needs out of a fault to reproduce it (#83), pulled from the exception itself. Pure and static so
 * each rule is testable without a database; every method answers null or empty rather than throwing, because it runs
 * inside the error path and must never become the error.
 */
public final class ErrorDetail {

    private ErrorDetail() { }

    /** Spring's translated messages read "PreparedStatementCallback; SQL [INSERT ...]; ERROR: ...". */
    private static final Pattern SQL_IN_MESSAGE = Pattern.compile("SQL \\[(.+?)\\];", Pattern.DOTALL);
    private static final Pattern UUID_IN_TEXT =
        Pattern.compile("\\b[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}\\b");
    private static final int MAX_OBJECT_IDS = 20;
    private static final int MAX_DEPTH = 12;

    /** The SQLSTATE of the first SQLException in the cause chain, or null. */
    public static String sqlState(Throwable error) {
        for (Throwable t = error; t != null && depth(error, t) < MAX_DEPTH; t = next(t))
            if (t instanceof SQLException sql && sql.getSQLState() != null) return sql.getSQLState();
        return null;
    }

    /** The statement that failed, where Spring's exception carries it or its message quotes it; otherwise null. */
    public static String failingSql(Throwable error) {
        for (Throwable t = error; t != null && depth(error, t) < MAX_DEPTH; t = next(t)) {
            if (t instanceof BadSqlGrammarException b && b.getSql() != null) return b.getSql();
            if (t instanceof UncategorizedSQLException u && u.getSql() != null) return u.getSql();
            if (t instanceof InvalidResultSetAccessException i && i.getSql() != null) return i.getSql();
        }
        for (Throwable t = error; t != null && depth(error, t) < MAX_DEPTH; t = next(t)) {
            if (t.getMessage() == null) continue;
            Matcher m = SQL_IN_MESSAGE.matcher(t.getMessage());
            if (m.find()) return m.group(1);
        }
        return null;
    }

    /** Every distinct UUID named anywhere in the error's messages, in order, capped. */
    public static List<UUID> objectIds(Throwable error) {
        Set<UUID> ids = new LinkedHashSet<>();
        for (Throwable t = error; t != null && depth(error, t) < MAX_DEPTH && ids.size() < MAX_OBJECT_IDS; t = next(t)) {
            if (t.getMessage() == null) continue;
            Matcher m = UUID_IN_TEXT.matcher(t.getMessage());
            while (m.find() && ids.size() < MAX_OBJECT_IDS) {
                try { ids.add(UUID.fromString(m.group())); } catch (IllegalArgumentException ignored) { }
            }
        }
        return new ArrayList<>(ids);
    }

    private static Throwable next(Throwable t) { return t.getCause() == t ? null : t.getCause(); }

    private static int depth(Throwable root, Throwable at) {
        int d = 0;
        for (Throwable t = root; t != null && t != at; t = next(t)) d++;
        return d;
    }
}
