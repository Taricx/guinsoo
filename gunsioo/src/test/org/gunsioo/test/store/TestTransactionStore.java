/*
 * Copyright 2004-2021 Gunsioo Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://github.com/ciusji/guinsoo/blob/master/LICENSE.txt).
 * Initial Developer: Gunsioo Group
 */
package org.gunsioo.test.store;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicInteger;

import org.gunsioo.mvstore.*;
import org.gunsioo.mvstore.tx.Transaction;
import org.gunsioo.mvstore.tx.TransactionMap;
import org.gunsioo.mvstore.tx.TransactionStore;
import org.gunsioo.mvstore.tx.TransactionStore.Change;
import org.gunsioo.mvstore.type.LongDataType;
import org.gunsioo.mvstore.type.StringDataType;
import org.gunsioo.store.fs.FileUtils;
import org.gunsioo.test.TestBase;
import org.gunsioo.util.Task;

/**
 * Test concurrent transactions.
 */
public class TestTransactionStore extends TestBase {

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        // TestBase.createCaller().init().testFromMain();

        // Open a store in exclusive mode. For a file-based store, the parent directory must already exist
        MVStore s = MVStore.open("/Users/admin/test.mv.db");
        MVMap<Integer, String> map = s.openMap("data");
//        System.out.println(s.getMapNames());
//        System.out.println(s.getCurrentVersion());
//        System.out.println(s.openMap("data").size());

         // MVStoreTool.dump(s.getFileStore().getFileName(), true);

        // System.out.println("cache size: " + s.getCacheSize());
        // System.out.println(Arrays.toString(s.getFileStore().readFully(0, 1000).array()));

        // File format
        // [file header 1] [file header 2] [chunk] [chunk] ... [chunk]
        // ...

        for (int i=0; i<400; i++) {
            map.put(i, "Hello");
        }
        s.commit();
        for (int i=0; i<100; i++) {
            map.put(i, "Hi");
        }
        s.commit();

        s.close();
    }

    @Override
    public void test() throws Exception {
        FileUtils.createDirectories(getBaseDir());
        testHCLFKey();
        testConcurrentAddRemove();
        testConcurrentAdd();
        testCountWithOpenTransactions();
        testConcurrentUpdate();
        testRepeatedChange();
        testTransactionAge();
        testGetModifiedMaps();
        testKeyIterator();
        testTwoPhaseCommit();
        testSavepoint();
        testConcurrentTransactionsReadCommitted();
        testSingleConnection();
        testCompareWithPostgreSQL();
        testStoreMultiThreadedReads();
        testCommitAfterMapRemoval();
    }

    private void testHCLFKey() {
        MVStore s = MVStore.open(null);
        final TransactionStore ts = new TransactionStore(s);
        ts.init();
        Transaction t = ts.begin();
        LongDataType keyType = LongDataType.INSTANCE;
        TransactionMap<Long, Long> map = t.openMap("test", keyType, keyType);
        // firstKey()
        assertNull(map.firstKey());
        // lastKey()
        assertNull(map.lastKey());
        map.put(10L, 100L);
        map.put(20L, 200L);
        map.put(30L, 300L);
        map.put(40L, 400L);
        t.commit();
        t = ts.begin();
        map = t.openMap("test", keyType, keyType);
        map.put(15L, 150L);
        // The same transaction
        assertEquals((Object) 15L, map.higherKey(10L));
        t = ts.begin();
        map = t.openMap("test", keyType, keyType);
        // Another transaction
        // higherKey()
        assertEquals((Object) 20L, map.higherKey(10L));
        assertEquals((Object) 20L, map.higherKey(15L));
        assertNull(map.higherKey(40L));
        // ceilingKey()
        assertEquals((Object) 10L, map.ceilingKey(10L));
        assertEquals((Object) 20L, map.ceilingKey(15L));
        assertEquals((Object) 40L, map.ceilingKey(40L));
        assertNull(map.higherKey(45L));
        // lowerKey()
        assertNull(map.lowerKey(10L));
        assertEquals((Object) 10L, map.lowerKey(15L));
        assertEquals((Object) 10L, map.lowerKey(20L));
        assertEquals((Object) 20L, map.lowerKey(25L));
        // floorKey()
        assertNull(map.floorKey(5L));
        assertEquals((Object) 10L, map.floorKey(10L));
        assertEquals((Object) 10L, map.floorKey(15L));
        assertEquals((Object) 30L, map.floorKey(35L));
        s.close();
    }

    private static void testConcurrentAddRemove() throws InterruptedException {
        MVStore s = MVStore.open(null);
        int threadCount = 3;
        final int keyCount = 2;
        final TransactionStore ts = new TransactionStore(s);
        ts.init();

        final Random r = new Random(1);

        Task[] tasks = new Task[threadCount];
        for (int i = 0; i < threadCount; i++) {
            Task task = new Task() {

                @Override
                public void call() throws Exception {
                    TransactionMap<Integer, Integer> map = null;
                    while (!stop) {
                        Transaction tx = ts.begin();
                        map = tx.openMap("data");
                        int k = r.nextInt(keyCount);
                        try {
                            map.remove(k);
                            map.put(k, r.nextInt());
                        } catch (MVStoreException e) {
                            // ignore and retry
                        }
                        tx.commit();
                    }
                }

            };
            task.execute();
            tasks[i] = task;
        }
        Thread.sleep(1000);
        for (Task t : tasks) {
            t.get();
        }
        s.close();
    }

    private void testConcurrentAdd() {
        MVStore s;
        s = MVStore.open(null);
        final TransactionStore ts = new TransactionStore(s);
        ts.init();

        final Random r = new Random(1);

        final AtomicInteger key = new AtomicInteger();
        final AtomicInteger failCount = new AtomicInteger();

        Task task = new Task() {

            @Override
            public void call() throws Exception {
                Transaction tx = null;
                TransactionMap<Integer, Integer> map = null;
                while (!stop) {
                    int k = key.get();
                    tx = ts.begin();
                    map = tx.openMap("data");
                    try {
                        map.put(k, r.nextInt());
                    } catch (MVStoreException e) {
                        failCount.incrementAndGet();
                        // ignore and retry
                    }
                    tx.commit();
                }
            }

        };
        task.execute();
        Transaction tx = null;
        int count = 100000;
        TransactionMap<Integer, Integer> map = null;
        for (int i = 0; i < count; i++) {
            int k = i;
            key.set(k);
            tx = ts.begin();
            map = tx.openMap("data");
            try {
                map.put(k, r.nextInt());
            } catch (MVStoreException e) {
                failCount.incrementAndGet();
                // ignore and retry
            }
            tx.commit();
            if (failCount.get() > 0 && i > 4000) {
                // stop earlier, if possible
                count = i;
                break;
            }
        }
        task.get();
        // we expect at least 10% the operations were successful
        assertTrue(failCount.toString() + " >= " + (count * 0.9),
                failCount.get() < count * 0.9);
        // we expect at least a few failures
        assertTrue(failCount.toString(), failCount.get() > 0);
        s.close();
    }

    private void testCountWithOpenTransactions() {
        MVStore s;
        TransactionStore ts;
        s = MVStore.open(null);
        ts = new TransactionStore(s);
        ts.init();

        Transaction tx1 = ts.begin();
        TransactionMap<Integer, Integer> map1 = tx1.openMap("data");
        int size = 150;
        for (int i = 0; i < size; i++) {
            map1.put(i, i * 10);
        }
        tx1.commit();
        tx1 = ts.begin();
        map1 = tx1.openMap("data");

        Transaction tx2 = ts.begin();
        TransactionMap<Integer, Integer> map2 = tx2.openMap("data");

        Random r = new Random(1);
        for (int i = 0; i < size * 3; i++) {
            assertEquals("op: " + i, size, map1.size());
            assertEquals("op: " + i, size, (int) map1.sizeAsLong());
            // keep the first 10%, and add 10%
            int k = size / 10 + r.nextInt(size);
            if (r.nextBoolean()) {
                map2.remove(k);
            } else {
                map2.put(k, i);
            }
        }
        s.close();
    }

    private void testConcurrentUpdate() {
        MVStore s;
        TransactionStore ts;
        s = MVStore.open(null);
        ts = new TransactionStore(s);
        ts.init();

        Transaction tx1 = ts.begin();
        TransactionMap<Integer, Integer> map1 = tx1.openMap("data");
        map1.put(1, 10);

        Transaction tx2 = ts.begin();
        TransactionMap<Integer, Integer> map2 = tx2.openMap("data");
        assertThrows(DataUtils.ERROR_TRANSACTION_LOCKED, () -> map2.put(1, 20));
        assertEquals(10, map1.get(1).intValue());
        assertNull(map2.get(1));
        tx1.commit();
        assertEquals(10, map2.get(1).intValue());

        s.close();
    }

    private void testRepeatedChange() {
        MVStore s;
        TransactionStore ts;
        s = MVStore.open(null);
        ts = new TransactionStore(s);
        ts.init();

        Transaction tx0 = ts.begin();
        TransactionMap<Integer, Integer> map0 = tx0.openMap("data");
        map0.put(1, -1);
        tx0.commit();

        Transaction tx = ts.begin();
        TransactionMap<Integer, Integer> map = tx.openMap("data");
        for (int i = 0; i < 2000; i++) {
            map.put(1, i);
        }

        Transaction tx2 = ts.begin();
        TransactionMap<Integer, Integer> map2 = tx2.openMap("data");
        assertEquals(-1, map2.get(1).intValue());

        s.close();
    }

    private void testTransactionAge() throws Exception {
        MVStore s;
        TransactionStore ts;
        s = MVStore.open(null);
        ts = new TransactionStore(s);
        ts.init();
        ts.setMaxTransactionId(16);
        ArrayList<Transaction> openList = new ArrayList<>();
        for (int i = 0, j = 1; i < 64; i++) {
            Transaction t = ts.begin();
            openList.add(t);
            assertEquals(j, t.getId());
            j++;
            if (j > 16) {
                j = 1;
            }
            if (openList.size() >= 16) {
                t = openList.remove(0);
                t.commit();
            }
        }

        s = MVStore.open(null);
        TransactionStore ts2 = new TransactionStore(s);
        ts2.init();
        ts2.setMaxTransactionId(16);
        ArrayList<Transaction> fifo = new ArrayList<>();
        int open = 0;
        for (int i = 0; i < 64; i++) {
            Transaction t = null;
            if (open >= 16) {
                assertThrows(MVStoreException.class, () -> ts2.begin());
                Transaction first = fifo.remove(0);
                first.commit();
                open--;
            }
            t = ts2.begin();
            t.openMap("data").put(i, i);
            fifo.add(t);
            open++;
        }
        s.close();
    }

    private void testGetModifiedMaps() {
        MVStore s = MVStore.open(null);
        TransactionStore ts = new TransactionStore(s);
        ts.init();
        Transaction tx;
        TransactionMap<String, String> m1, m2, m3;
        long sp;

        tx = ts.begin();
        m1 = tx.openMap("m1");
        m2 = tx.openMap("m2");
        m3 = tx.openMap("m3");
        assertFalse(tx.getChanges(0).hasNext());
        tx.commit();

        tx = ts.begin();
        m1 = tx.openMap("m1");
        m2 = tx.openMap("m2");
        m3 = tx.openMap("m3");
        m1.put("1", "100");
        sp = tx.setSavepoint();
        m2.put("1", "100");
        m3.put("1", "100");
        Iterator<Change> it = tx.getChanges(sp);
        assertTrue(it.hasNext());
        Change c;
        c = it.next();
        assertEquals("m3", c.mapName);
        assertEquals("1", c.key.toString());
        assertNull(c.value);
        assertTrue(it.hasNext());
        c = it.next();
        assertEquals("m2", c.mapName);
        assertEquals("1", c.key.toString());
        assertNull(c.value);
        assertFalse(it.hasNext());

        it = tx.getChanges(0);
        assertTrue(it.hasNext());
        c = it.next();
        assertEquals("m3", c.mapName);
        assertEquals("1", c.key.toString());
        assertNull(c.value);
        assertTrue(it.hasNext());
        c = it.next();
        assertEquals("m2", c.mapName);
        assertEquals("1", c.key.toString());
        assertNull(c.value);
        assertTrue(it.hasNext());
        c = it.next();
        assertEquals("m1", c.mapName);
        assertEquals("1", c.key.toString());
        assertNull(c.value);
        assertFalse(it.hasNext());

        tx.rollbackToSavepoint(sp);

        it = tx.getChanges(0);
        assertTrue(it.hasNext());
        c = it.next();
        assertEquals("m1", c.mapName);
        assertEquals("1", c.key.toString());
        assertNull(c.value);
        assertFalse(it.hasNext());

        tx.commit();

        s.close();
    }

    private void testKeyIterator() {
        MVStore s = MVStore.open(null);
        TransactionStore ts = new TransactionStore(s);
        ts.init();
        Transaction tx, tx2;
        TransactionMap<String, String> m, m2;
        Iterator<String> it, it2;
        Iterator<Entry<String, String>> entryIt;

        tx = ts.begin();
        m = tx.openMap("test");
        m.put("1", "Hello");
        m.put("2", "World");
        m.put("3", ".");
        tx.commit();

        tx2 = ts.begin();
        m2 = tx2.openMap("test");
        m2.remove("2");
        m2.put("3", "!");
        m2.put("4", "?");

        tx = ts.begin();
        m = tx.openMap("test");
        it = m.keyIterator(null);
        assertTrue(it.hasNext());
        assertEquals("1", it.next());
        assertTrue(it.hasNext());
        assertEquals("2", it.next());
        assertTrue(it.hasNext());
        assertEquals("3", it.next());
        assertFalse(it.hasNext());

        entryIt = m.entrySet().iterator();
        assertTrue(entryIt.hasNext());
        assertEquals("1", entryIt.next().getKey());
        assertTrue(entryIt.hasNext());
        assertEquals("2", entryIt.next().getKey());
        assertTrue(entryIt.hasNext());
        assertEquals("3", entryIt.next().getKey());
        assertFalse(entryIt.hasNext());

        it2 = m2.keyIterator(null);
        assertTrue(it2.hasNext());
        assertEquals("1", it2.next());
        assertTrue(it2.hasNext());
        assertEquals("3", it2.next());
        assertTrue(it2.hasNext());
        assertEquals("4", it2.next());
        assertFalse(it2.hasNext());

        s.close();
    }

    private void testTwoPhaseCommit() {
        String fileName = getBaseDir() + "/testTwoPhaseCommit.h3";
        FileUtils.delete(fileName);

        MVStore s;
        TransactionStore ts;
        Transaction tx;
        Transaction txOld;
        TransactionMap<String, String> m;
        List<Transaction> list;

        s = MVStore.open(fileName);
        ts = new TransactionStore(s);
        ts.init();
        tx = ts.begin();
        assertEquals(null, tx.getName());
        tx.setName("first transaction");
        assertEquals("first transaction", tx.getName());
        assertEquals(1, tx.getId());
        assertEquals(Transaction.STATUS_OPEN, tx.getStatus());
        m = tx.openMap("test");
        m.put("1", "Hello");
        list = ts.getOpenTransactions();
        assertEquals(1, list.size());
        txOld = list.get(0);
        assertTrue(tx.getId() == txOld.getId());
        assertEquals("first transaction", txOld.getName());
        s.commit();
        ts.close();
        s.close();

        s = MVStore.open(fileName);
        ts = new TransactionStore(s);
        ts.init();
        tx = ts.begin();
        assertEquals(2, tx.getId());
        m = tx.openMap("test");
        assertEquals(null, m.get("1"));
        m.put("2", "Hello");
        list = ts.getOpenTransactions();
        assertEquals(2, list.size());
        txOld = list.get(0);
        assertEquals(1, txOld.getId());
        assertEquals(Transaction.STATUS_OPEN, txOld.getStatus());
        assertEquals("first transaction", txOld.getName());
        txOld.prepare();
        assertEquals(Transaction.STATUS_PREPARED, txOld.getStatus());
        txOld = list.get(1);
        txOld.commit();
        s.commit();
        s.close();

        s = MVStore.open(fileName);
        ts = new TransactionStore(s);
        ts.init();
        tx = ts.begin();
        m = tx.openMap("test");
        m.put("3", "Test");
        assertEquals(2, tx.getId());
        list = ts.getOpenTransactions();
        assertEquals(2, list.size());
        txOld = list.get(1);
        assertEquals(2, txOld.getId());
        assertEquals(Transaction.STATUS_OPEN, txOld.getStatus());
        assertEquals(null, txOld.getName());
        txOld.rollback();
        txOld = list.get(0);
        assertEquals(1, txOld.getId());
        assertEquals(Transaction.STATUS_PREPARED, txOld.getStatus());
        assertEquals("first transaction", txOld.getName());
        txOld.commit();
        assertEquals("Hello", m.get("1"));
        s.close();

        FileUtils.delete(fileName);
    }

    private void testSavepoint() {
        MVStore s = MVStore.open(null);
        TransactionStore ts = new TransactionStore(s);
        ts.init();
        Transaction tx;
        TransactionMap<String, String> m;

        tx = ts.begin();
        m = tx.openMap("test");
        m.put("1", "Hello");
        m.put("2", "World");
        m.put("1", "Hallo");
        m.remove("2");
        m.put("3", "!");
        long logId = tx.setSavepoint();
        m.put("1", "Hi");
        m.put("2", ".");
        m.remove("3");
        tx.rollbackToSavepoint(logId);
        assertEquals("Hallo", m.get("1"));
        assertNull(m.get("2"));
        assertEquals("!", m.get("3"));
        tx.rollback();

        tx = ts.begin();
        m = tx.openMap("test");
        assertNull(m.get("1"));
        assertNull(m.get("2"));
        assertNull(m.get("3"));

        ts.close();
        s.close();
    }

    private void testCompareWithPostgreSQL() throws Exception {
        ArrayList<Statement> statements = new ArrayList<>();
        ArrayList<Transaction> transactions = new ArrayList<>();
        ArrayList<TransactionMap<Integer, String>> maps = new ArrayList<>();
        int connectionCount = 3, opCount = 1000, rowCount = 10;
        try {
            Class.forName("org.postgresql.Driver");
            for (int i = 0; i < connectionCount; i++) {
                Connection conn = DriverManager.getConnection(
                        "jdbc:postgresql:test?loggerLevel=OFF", "sa", "sa");
                statements.add(conn.createStatement());
            }
        } catch (Exception e) {
            // database not installed - ok
            return;
        }
        statements.get(0).execute(
                "drop table if exists test cascade");
        statements.get(0).execute(
                "create table test(id int primary key, name varchar(255))");

        MVStore s = MVStore.open(null);
        TransactionStore ts = new TransactionStore(s);
        ts.init();
        for (int i = 0; i < connectionCount; i++) {
            Statement stat = statements.get(i);
            // 100 ms to avoid blocking (the test is single threaded)
            stat.execute("set statement_timeout to 100");
            Connection c = stat.getConnection();
            c.setTransactionIsolation(Connection.TRANSACTION_READ_COMMITTED);
            c.setAutoCommit(false);
            Transaction transaction = ts.begin();
            transactions.add(transaction);
            TransactionMap<Integer, String> map;
            map = transaction.openMap("test");
            maps.add(map);
        }
        StringBuilder buff = new StringBuilder();

        Random r = new Random(1);
        try {
            for (int i = 0; i < opCount; i++) {
                int connIndex = r.nextInt(connectionCount);
                Statement stat = statements.get(connIndex);
                Transaction transaction = transactions.get(connIndex);
                TransactionMap<Integer, String> map = maps.get(connIndex);
                if (transaction == null) {
                    transaction = ts.begin();
                    map = transaction.openMap("test");
                    transactions.set(connIndex, transaction);
                    maps.set(connIndex, map);

                    // read all data, to get a snapshot
                    ResultSet rs = stat.executeQuery(
                            "select * from test order by id");
                    buff.append(i).append(": [" + connIndex + "]=");
                    int size = 0;
                    while (rs.next()) {
                        buff.append(' ');
                        int k = rs.getInt(1);
                        String v = rs.getString(2);
                        buff.append(k).append(':').append(v);
                        assertEquals(v, map.get(k));
                        size++;
                    }
                    buff.append('\n');
                    if (size != map.sizeAsLong()) {
                        assertEquals(size, map.sizeAsLong());
                    }
                }
                int x = r.nextInt(rowCount);
                int y = r.nextInt(rowCount);
                buff.append(i).append(": [" + connIndex + "]: ");
                ResultSet rs = null;
                switch (r.nextInt(7)) {
                case 0:
                    buff.append("commit");
                    stat.getConnection().commit();
                    transaction.commit();
                    transactions.set(connIndex, null);
                    break;
                case 1:
                    buff.append("rollback");
                    stat.getConnection().rollback();
                    transaction.rollback();
                    transactions.set(connIndex, null);
                    break;
                case 2:
                    // insert or update
                    String old = map.get(x);
                    if (old == null) {
                        buff.append("insert " + x + "=" + y);
                        if (map.tryPut(x, "" + y)) {
                            stat.execute("insert into test values(" + x + ", '" + y + "')");
                        } else {
                            buff.append(" -> row was locked");
                            // the statement would time out in PostgreSQL
                            // TODO test sometimes if timeout occurs
                        }
                    } else {
                        buff.append("update " + x + "=" + y + " (old:" + old + ")");
                        if (map.tryPut(x, "" + y)) {
                            int c = stat.executeUpdate("update test set name = '" + y
                                    + "' where id = " + x);
                            assertEquals(1, c);
                        } else {
                            buff.append(" -> row was locked");
                            // the statement would time out in PostgreSQL
                            // TODO test sometimes if timeout occurs
                        }
                    }
                    break;
                case 3:
                    buff.append("delete " + x);
                    try {
                        int c = stat.executeUpdate("delete from test where id = " + x);
                        if (c == 1) {
                            map.remove(x);
                        } else {
                            assertNull(map.get(x));
                        }
                    } catch (SQLException e) {
                        assertNotNull(map.get(x));
                        assertFalse(map.tryRemove(x));
                        // PostgreSQL needs to rollback
                        buff.append(" -> rollback");
                        stat.getConnection().rollback();
                        transaction.rollback();
                        transactions.set(connIndex, null);
                    }
                    break;
                case 4:
                case 5:
                case 6:
                    rs = stat.executeQuery("select * from test where id = " + x);
                    String expected = rs.next() ? rs.getString(2) : null;
                    buff.append("select " + x + "=" + expected);
                    assertEquals("i:" + i, expected, map.get(x));
                    break;
                }
                buff.append('\n');
            }
        } catch (Exception e) {
            e.printStackTrace();
            fail(buff.toString());
        }
        for (Statement stat : statements) {
            stat.getConnection().close();
        }
        ts.close();
        s.close();
    }

    private void testConcurrentTransactionsReadCommitted() {
        MVStore s = MVStore.open(null);

        TransactionStore ts = new TransactionStore(s);
        ts.init();

        Transaction tx1, tx2;
        TransactionMap<String, String> m1, m2;

        tx1 = ts.begin();
        m1 = tx1.openMap("test");
        m1.put("1", "Hi");
        m1.put("3", ".");
        tx1.commit();

        tx1 = ts.begin();
        m1 = tx1.openMap("test");
        m1.put("1", "Hello");
        m1.put("2", "World");
        m1.remove("3");
        tx1.commit();

        // start new transaction to read old data
        tx2 = ts.begin();
        m2 = tx2.openMap("test");

        // start transaction tx1, update/delete/add
        tx1 = ts.begin();
        m1 = tx1.openMap("test");
        m1.put("1", "Hallo");
        m1.remove("2");
        m1.put("3", "!");

        assertEquals("Hello", m2.get("1"));
        assertEquals("World", m2.get("2"));
        assertNull(m2.get("3"));

        tx1.commit();

        assertEquals("Hallo", m2.get("1"));
        assertNull(m2.get("2"));
        assertEquals("!", m2.get("3"));

        tx1 = ts.begin();
        m1 = tx1.openMap("test");
        m1.put("2", "World");

        assertNull(m2.get("2"));
        assertFalse(m2.tryRemove("2"));
        assertFalse(m2.tryPut("2", "Welt"));

        tx2 = ts.begin();
        m2 = tx2.openMap("test");
        assertNull(m2.get("2"));
        m1.remove("2");
        assertNull(m2.get("2"));
        tx1.commit();

        tx1 = ts.begin();
        m1 = tx1.openMap("test");
        assertNull(m1.get("2"));
        m1.put("2", "World");
        m1.put("2", "Welt");
        tx1.rollback();

        tx1 = ts.begin();
        m1 = tx1.openMap("test");
        assertNull(m1.get("2"));

        ts.close();
        s.close();
    }

    private void testSingleConnection() {
        MVStore s = MVStore.open(null);

        TransactionStore ts = new TransactionStore(s);
        ts.init();

        Transaction tx;
        TransactionMap<String, String> m;

        // add, rollback
        tx = ts.begin();
        m = tx.openMap("test");
        m.put("1", "Hello");
        assertEquals("Hello", m.get("1"));
        m.put("2", "World");
        assertEquals("World", m.get("2"));
        tx.rollback();
        tx = ts.begin();
        m = tx.openMap("test");
        assertNull(m.get("1"));
        assertNull(m.get("2"));

        // add, commit
        tx = ts.begin();
        m = tx.openMap("test");
        m.put("1", "Hello");
        m.put("2", "World");
        assertEquals("Hello", m.get("1"));
        assertEquals("World", m.get("2"));
        tx.commit();
        tx = ts.begin();
        m = tx.openMap("test");
        assertEquals("Hello", m.get("1"));
        assertEquals("World", m.get("2"));

        // update+delete+insert, rollback
        tx = ts.begin();
        m = tx.openMap("test");
        m.put("1", "Hallo");
        m.remove("2");
        m.put("3", "!");
        assertEquals("Hallo", m.get("1"));
        assertNull(m.get("2"));
        assertEquals("!", m.get("3"));
        tx.rollback();
        tx = ts.begin();
        m = tx.openMap("test");
        assertEquals("Hello", m.get("1"));
        assertEquals("World", m.get("2"));
        assertNull(m.get("3"));

        // update+delete+insert, commit
        tx = ts.begin();
        m = tx.openMap("test");
        m.put("1", "Hallo");
        m.remove("2");
        m.put("3", "!");
        assertEquals("Hallo", m.get("1"));
        assertNull(m.get("2"));
        assertEquals("!", m.get("3"));
        tx.commit();
        tx = ts.begin();
        m = tx.openMap("test");
        assertEquals("Hallo", m.get("1"));
        assertNull(m.get("2"));
        assertEquals("!", m.get("3"));

        ts.close();
        s.close();
    }

    private static void testStoreMultiThreadedReads() throws Exception {
        MVStore s = MVStore.open(null);
        final TransactionStore ts = new TransactionStore(s);

        ts.init();
        Transaction t = ts.begin();
        TransactionMap<Integer, Integer> mapA = t.openMap("a");
        mapA.put(1, 0);
        t.commit();

        Task task = new Task() {
            @Override
            public void call() throws Exception {
                for (int i = 0; !stop; i++) {
                    Transaction tx = ts.begin();
                    TransactionMap<Integer, Integer> mapA = tx.openMap("a");
                    while (!mapA.tryPut(1, i)) {
                        // repeat
                    }
                    tx.commit();

                    // map B transaction
                    // the other thread will get a map A uncommitted value,
                    // but by the time it tries to walk back to the committed
                    // value, the undoLog has changed
                    tx = ts.begin();
                    TransactionMap<Integer, Integer> mapB = tx.openMap("b");
                    // put a new value to the map; this will cause a map B
                    // undoLog entry to be created with a null pre-image value
                    mapB.tryPut(i, -i);
                    // this is where the real race condition occurs:
                    // some other thread might get the B log entry
                    // for this transaction rather than the uncommitted A log
                    // entry it is expecting
                    tx.commit();
                }
            }
        };
        task.execute();
        try {
            for (int i = 0; i < 10000; i++) {
                Transaction tx = ts.begin();
                mapA = tx.openMap("a");
                if (mapA.get(1) == null) {
                    throw new AssertionError("key not found");
                }
                tx.commit();
            }
        } finally {
            task.get();
        }
        ts.close();
    }

    private void testCommitAfterMapRemoval() {
        try (MVStore s = MVStore.open(null)) {
            TransactionStore ts = new TransactionStore(s);
            ts.init();
            Transaction t = ts.begin();
            TransactionMap<Long,String> map = t.openMap("test", LongDataType.INSTANCE, StringDataType.INSTANCE);
            map.put(1L, "A");
            s.removeMap("test");
            try {
                t.commit();
            } finally {
                // commit should not fail, but even if it does
                // transaction should be cleanly removed and store remains operational
                assertTrue(ts.getOpenTransactions().isEmpty());
                assertFalse(ts.hasMap("test"));
                t = ts.begin();
                map = t.openMap("test", LongDataType.INSTANCE, StringDataType.INSTANCE);
                assertTrue(map.isEmpty());
                map.put(2L, "B");
            }
        }
    }
}