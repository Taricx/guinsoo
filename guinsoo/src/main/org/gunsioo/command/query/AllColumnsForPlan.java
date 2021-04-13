/*
 * Copyright 2004-2021 Gunsioo Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://github.com/ciusji/guinsoo/blob/master/LICENSE.txt).
 * Initial Developer: Gunsioo Group
 */
package org.gunsioo.command.query;

import java.util.ArrayList;
import java.util.HashMap;
import org.gunsioo.expression.ExpressionVisitor;
import org.gunsioo.table.Column;
import org.gunsioo.table.Table;
import org.gunsioo.table.TableFilter;

/**
 * This information is expensive to compute for large queries, so do so
 * on-demand. Also store the information pre-mapped by table to avoid expensive
 * traversal.
 */
public class AllColumnsForPlan {

    private final TableFilter[] filters;
    private HashMap<Table, ArrayList<Column>> map;

    public AllColumnsForPlan(TableFilter[] filters) {
        this.filters = filters;
    }

    /**
     * Called by ExpressionVisitor.
     *
     * @param newCol new column to be added.
     */
    public void add(Column newCol) {
        ArrayList<Column> cols = map.get(newCol.getTable());
        if (cols == null) {
            cols = new ArrayList<>();
            map.put(newCol.getTable(), cols);
        }
        if (!cols.contains(newCol))
            cols.add(newCol);
    }

    /**
     * Used by index to calculate the cost of a scan.
     *
     * @param table the table.
     * @return all table's referenced columns.
     */
    public ArrayList<Column> get(Table table) {
        if (map == null) {
            map = new HashMap<>();
            ExpressionVisitor.allColumnsForTableFilters(filters, this);
        }
        return map.get(table);
    }

}