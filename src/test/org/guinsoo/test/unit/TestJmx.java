/*
 * Copyright 2004-2021 Guinsoo Group. Multiple-Licensed under the MPL 2.0,
 * and the EPL 1.0 (https://github.com/ciusji/guinsoo/blob/master/LICENSE.txt).
 * Initial Developer: Guinsoo Group
 */
package org.guinsoo.test.unit;

import java.lang.management.ManagementFactory;
import java.sql.Connection;
import java.sql.Statement;
import java.util.HashMap;
import java.util.Set;
import javax.management.Attribute;
import javax.management.MBeanAttributeInfo;
import javax.management.MBeanInfo;
import javax.management.MBeanOperationInfo;
import javax.management.MBeanServer;
import javax.management.ObjectName;
import org.guinsoo.engine.Constants;
import org.guinsoo.test.TestBase;
import org.guinsoo.test.TestDb;
import org.guinsoo.util.Utils;

/**
 * Tests the JMX feature.
 */
public class TestJmx extends TestDb {

    /**
     * Run just this test.
     *
     * @param a ignored
     */
    public static void main(String... a) throws Exception {
        TestBase base = TestBase.createCaller().init();
        base.testFromMain();
    }

    @Override
    public void test() throws Exception {
        HashMap<String, MBeanAttributeInfo> attrMap;
        HashMap<String, MBeanOperationInfo> opMap;
        String result;
        MBeanInfo info;
        ObjectName name;
        Connection conn;
        Statement stat;

        MBeanServer mbeanServer = ManagementFactory.getPlatformMBeanServer();

        conn = getConnection("mem:jmx;jmx=true");
        stat = conn.createStatement();

        name = new ObjectName("org.guinsoo:name=JMX,path=mem_jmx");
        info = mbeanServer.getMBeanInfo(name);
        assertEquals("0", mbeanServer.
                getAttribute(name, "CacheSizeMax").toString());
        // cache size is ignored for in-memory databases
        mbeanServer.setAttribute(name, new Attribute("CacheSizeMax", 1));
        assertEquals("0", mbeanServer.
                getAttribute(name, "CacheSizeMax").toString());
        assertEquals("0", mbeanServer.
                getAttribute(name, "CacheSize").toString());
        assertEquals("false", mbeanServer.
                getAttribute(name, "Exclusive").toString());
        assertEquals("0", mbeanServer.
                getAttribute(name, "FileSize").toString());
        assertEquals("0", mbeanServer.
                getAttribute(name, "FileReadCount").toString());
        assertEquals("0", mbeanServer.
                getAttribute(name, "FileWriteCount").toString());
        assertEquals("0", mbeanServer.
                getAttribute(name, "FileWriteCountTotal").toString());
        assertEquals("REGULAR", mbeanServer.
                getAttribute(name, "Mode").toString());
        if (config.mvStore) {
            assertEquals("true", mbeanServer.getAttribute(name, "MultiThreaded").toString());
            assertEquals("true", mbeanServer.getAttribute(name, "Mvcc").toString());
        } else {
            assertEquals("false", mbeanServer.getAttribute(name, "MultiThreaded").toString());
            assertEquals("false", mbeanServer.getAttribute(name, "Mvcc").toString());
        }
        assertEquals("false", mbeanServer.
                getAttribute(name, "ReadOnly").toString());
        assertEquals("1", mbeanServer.
                getAttribute(name, "TraceLevel").toString());
        mbeanServer.setAttribute(name, new Attribute("TraceLevel", 0));
        assertEquals("0", mbeanServer.
                getAttribute(name, "TraceLevel").toString());
        assertEquals(Constants.FULL_VERSION, mbeanServer.getAttribute(name, "Version").toString());
        assertEquals(14, info.getAttributes().length);
        result = mbeanServer.invoke(name, "listSettings", null, null).toString();
        assertContains(result, "ANALYZE_AUTO");

        conn.setAutoCommit(false);
        stat.execute("create table test(id int)");
        stat.execute("insert into test values(1)");

        result = mbeanServer.invoke(name, "listSessions", null, null).toString();
        assertContains(result, "session id");
        if (config.mvStore) {
            assertContains(result, "read lock");
        } else {
            assertContains(result, "write lock");
        }

        assertEquals(2, info.getOperations().length);
        assertContains(info.getDescription(), "database");
        attrMap = new HashMap<>();
        for (MBeanAttributeInfo a : info.getAttributes()) {
            attrMap.put(a.getName(), a);
        }
        assertContains(attrMap.get("CacheSize").getDescription(), "KB");
        opMap = new HashMap<>();
        for (MBeanOperationInfo o : info.getOperations()) {
            opMap.put(o.getName(), o);
        }
        assertContains(opMap.get("listSessions").getDescription(), "lock");
        assertEquals(MBeanOperationInfo.INFO, opMap.get("listSessions").getImpact());

        conn.close();

        conn = getConnection("jmx;jmx=true");
        conn.close();
        conn = getConnection("jmx;jmx=true");

        name = new ObjectName("org.guinsoo:name=JMX,*");
        @SuppressWarnings("rawtypes")
        Set set = mbeanServer.queryNames(name, null);
        name = (ObjectName) set.iterator().next();

        if (config.memory) {
            assertEquals("0", mbeanServer.
                    getAttribute(name, "CacheSizeMax").toString());
        } else if (config.mvStore) {
            assertEquals("16384", mbeanServer.
                    getAttribute(name, "CacheSizeMax").toString());
        } else {
            int cacheSize = Utils.scaleForAvailableMemory(
                    Constants.CACHE_SIZE_DEFAULT);
            assertEquals("" + cacheSize, mbeanServer.
                    getAttribute(name, "CacheSizeMax").toString());
        }
        mbeanServer.setAttribute(name, new Attribute("CacheSizeMax", 1));
        if (config.memory) {
            assertEquals("0", mbeanServer.
                    getAttribute(name, "CacheSizeMax").toString());
        } else if (config.mvStore) {
            assertEquals("1024", mbeanServer.
                    getAttribute(name, "CacheSizeMax").toString());
            assertEquals("0", mbeanServer.
                    getAttribute(name, "CacheSize").toString());
            assertTrue(0 < (Long) mbeanServer.
                    getAttribute(name, "FileReadCount"));
            // FileWriteCount can be not yet updated and may return 0
            assertTrue(0 <= (Long) mbeanServer.getAttribute(name, "FileWriteCount"));
            assertEquals("0", mbeanServer.
                    getAttribute(name, "FileWriteCountTotal").toString());
        } else {
            assertEquals("1", mbeanServer.
                    getAttribute(name, "CacheSizeMax").toString());
            assertTrue(0 < (Integer) mbeanServer.
                    getAttribute(name, "CacheSize"));
            assertTrue(0 < (Long) mbeanServer.
                    getAttribute(name, "FileSize"));
            assertTrue(0 < (Long) mbeanServer.
                    getAttribute(name, "FileReadCount"));
            assertTrue(0 < (Long) mbeanServer.
                    getAttribute(name, "FileWriteCount"));
            assertTrue(0 < (Long) mbeanServer.
                    getAttribute(name, "FileWriteCountTotal"));
            mbeanServer.setAttribute(name, new Attribute("LogMode", 0));
            assertEquals("0", mbeanServer.getAttribute(name, "LogMode").toString());
        }

        conn.close();

    }

}