package com.bankservices.bankservices_api.security;

import org.hibernate.resource.jdbc.spi.StatementInspector;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Test-only Hibernate hook: records every SQL statement text sent to the
 * database, so tests can prove user input is bound as a parameter ("?") and
 * never concatenated into the statement.
 */
public class SqlCapturingInspector implements StatementInspector {

    public static final List<String> STATEMENTS = new CopyOnWriteArrayList<>();

    @Override
    public String inspect(String sql) {
        STATEMENTS.add(sql);
        return sql;
    }
}
