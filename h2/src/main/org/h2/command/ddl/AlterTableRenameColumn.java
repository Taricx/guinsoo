/*
 * Copyright 2004-2021 H2 Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://h2database.com/html/license.html).
 * Initial Developer: H2 Group
 */
package org.h2.command.ddl;

import org.h2.api.ErrorCode;
import org.h2.command.CommandInterface;
import org.h2.constraint.ConstraintReferential;
import org.h2.engine.Database;
import org.h2.engine.DbObject;
import org.h2.engine.Right;
import org.h2.engine.SessionLocal;
import org.h2.message.DbException;
import org.h2.schema.Schema;
import org.h2.table.Column;
import org.h2.table.Table;

/**
 * This class represents the statement
 * ALTER TABLE ALTER COLUMN RENAME
 */
public class AlterTableRenameColumn extends SchemaCommand {

    private boolean ifTableExists;
    private boolean ifExists;
    private String tableName;
    private String oldName;
    private String newName;

    public AlterTableRenameColumn(SessionLocal session, Schema schema) {
        super(session, schema);
    }

    public void setIfTableExists(boolean b) {
        this.ifTableExists = b;
    }

    public void setIfExists(boolean b) {
        this.ifExists = b;
    }

    public void setTableName(String tableName) {
        this.tableName = tableName;
    }

    public void setOldColumnName(String oldName) {
        this.oldName = oldName;
    }

    public void setNewColumnName(String newName) {
        this.newName = newName;
    }

    @Override
    public long update() {
        Database db = session.getDatabase();
        Table table = getSchema().findTableOrView(session, tableName);
        if (table == null) {
            if (ifTableExists) {
                return 0;
            }
            throw DbException.get(ErrorCode.TABLE_OR_VIEW_NOT_FOUND_1, tableName);
        }
        Column column = table.getColumn(oldName, ifExists);
        if (column == null) {
            return 0;
        }
        session.getUser().checkTableRight(table, Right.SCHEMA_OWNER);
        table.checkSupportAlter();
        table.renameColumn(column, newName);
        table.setModified();
        db.updateMeta(session, table);

        // if we have foreign key constraints pointing at this table, we need to update them
        for (DbObject childDbObject : table.getChildren()) {
            if (childDbObject instanceof ConstraintReferential) {
                ConstraintReferential ref = (ConstraintReferential) childDbObject;
                ref.updateOnTableColumnRename();
            }
        }

        for (DbObject child : table.getChildren()) {
            if (child.getCreateSQL() != null) {
                db.updateMeta(session, child);
            }
        }
        return 0;
    }

    @Override
    public int getType() {
        return CommandInterface.ALTER_TABLE_ALTER_COLUMN_RENAME;
    }

}
