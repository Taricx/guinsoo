/*
 * Copyright 2004-2021 Guinsoo Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://github.com/ciusji/guinsoo/blob/master/LICENSE.txt).
 * Initial Developer: Guinsoo Group
 */
package org.guinsoo.schema;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Map;

import org.guinsoo.engine.Database;
import org.guinsoo.engine.SessionLocal;
import org.guinsoo.engine.User;
import org.guinsoo.table.Table;

/**
 * Meta data schema.
 */
public abstract class MetaSchema extends Schema {

    /**
     * Creates a new instance of meta data schema.
     *
     * @param database
     *            the database
     * @param id
     *            the object id
     * @param schemaName
     *            the schema name
     * @param owner
     *            the owner of the schema
     */
    public MetaSchema(Database database, int id, String schemaName, User owner) {
        super(database, id, schemaName, owner, true);
    }

    @Override
    public Table findTableOrView(SessionLocal session, String name) {
        Map<String, Table> map = getMap(session);
        Table table = map.get(name);
        if (table != null) {
            return table;
        }
        return super.findTableOrView(session, name);
    }

    @Override
    public Collection<Table> getAllTablesAndViews(SessionLocal session) {
        Collection<Table> userTables = super.getAllTablesAndViews(session);
        if (session == null) {
            return userTables;
        }
        Collection<Table> systemTables = getMap(session).values();
        if (userTables.isEmpty()) {
            return systemTables;
        }
        ArrayList<Table> list = new ArrayList<>(systemTables.size() + userTables.size());
        list.addAll(systemTables);
        list.addAll(userTables);
        return list;
    }

    @Override
    public Table getTableOrView(SessionLocal session, String name) {
        Map<String, Table> map = getMap(session);
        Table table = map.get(name);
        if (table != null) {
            return table;
        }
        return super.getTableOrView(session, name);
    }

    @Override
    public Table getTableOrViewByName(SessionLocal session, String name) {
        Map<String, Table> map = getMap(session);
        Table table = map.get(name);
        if (table != null) {
            return table;
        }
        return super.getTableOrViewByName(session, name);
    }

    /**
     * Returns map of tables in this schema.
     *
     * @param session the session
     * @return map of tables in this schema
     */
    protected abstract Map<String, Table> getMap(SessionLocal session);

    @Override
    public boolean isEmpty() {
        return false;
    }

}