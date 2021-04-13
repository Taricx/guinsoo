/*
 * Copyright 2004-2021 Gunsioo Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://github.com/ciusji/guinsoo/blob/master/LICENSE.txt).
 * Initial Developer: Gunsioo Group
 */
package org.gunsioo.table;

import org.gunsioo.command.dml.DataChangeStatement;
import org.gunsioo.engine.SessionLocal;
import org.gunsioo.expression.Expression;
import org.gunsioo.expression.ExpressionColumn;
import org.gunsioo.result.LocalResult;
import org.gunsioo.result.ResultInterface;
import org.gunsioo.result.ResultTarget;
import org.gunsioo.result.Row;
import org.gunsioo.schema.Schema;

/**
 * A data change delta table.
 */
public class DataChangeDeltaTable extends VirtualConstructedTable {

    /**
     * Result option.
     */
    public enum ResultOption {

        /**
         * OLD row.
         */
        OLD,

        /**
         * NEW row with evaluated default expressions, but before triggers.
         */
        NEW,

        /**
         * FINAL rows after triggers.
         */
        FINAL;

    }

    /**
     * Collects final row for INSERT operations.
     *
     * @param session
     *            the session
     * @param table
     *            the table
     * @param deltaChangeCollector
     *            target result
     * @param deltaChangeCollectionMode
     *            collection mode
     * @param newRow
     *            the inserted row
     */
    public static void collectInsertedFinalRow(SessionLocal session, Table table, ResultTarget deltaChangeCollector,
            ResultOption deltaChangeCollectionMode, Row newRow) {
        if (session.getMode().takeInsertedIdentity) {
            Column column = table.getIdentityColumn();
            if (column != null) {
                session.setLastIdentity(newRow.getValue(column.getColumnId()));
            }
        }
        if (deltaChangeCollectionMode == ResultOption.FINAL) {
            deltaChangeCollector.addRow(newRow.getValueList());
        }
    }

    private final DataChangeStatement statement;

    private final ResultOption resultOption;

    private final Expression[] expressions;

    public DataChangeDeltaTable(Schema schema, SessionLocal session, DataChangeStatement statement,
            ResultOption resultOption) {
        super(schema, 0, statement.getStatementName());
        this.statement = statement;
        this.resultOption = resultOption;
        Table table = statement.getTable();
        Column[] tableColumns = table.getColumns();
        int columnCount = tableColumns.length;
        Column[] c = new Column[columnCount];
        for (int i = 0; i < columnCount; i++) {
            c[i] = tableColumns[i].getClone();
        }
        setColumns(c);
        Expression[] expressions = new Expression[columnCount];
        String tableName = getName();
        for (int i = 0; i < columnCount; i++) {
            expressions[i] = new ExpressionColumn(database, null, tableName, c[i].getName());
        }
        this.expressions = expressions;
    }

    @Override
    public boolean canGetRowCount(SessionLocal session) {
        return false;
    }

    @Override
    public long getRowCount(SessionLocal session) {
        return Long.MAX_VALUE;
    }

    @Override
    public long getRowCountApproximation(SessionLocal session) {
        return Long.MAX_VALUE;
    }

    @Override
    public ResultInterface getResult(SessionLocal session) {
        statement.prepare();
        int columnCount = expressions.length;
        LocalResult result = new LocalResult(session, expressions, columnCount, columnCount);
        statement.update(result, resultOption);
        return result;
    }

    @Override
    public StringBuilder getSQL(StringBuilder builder, int sqlFlags) {
        return builder.append(resultOption.name()).append(" TABLE (").append(statement.getSQL()).append(')');
    }

    @Override
    public boolean isDeterministic() {
        return false;
    }

}