/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.guinsoo;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.Map;

import static org.guinsoo.util.Utils.readSettingsFromURL;

/**
 * ConnectionBuilder
 *
 * @author cius.ji
 * @since 1.8+
 */
public class ConnectionBuilder {
    /**
     * database url
     */
    private String url;

    /**
     * database trace enable, default is false.
     */
    private boolean traceEnable;

    /**
     * database multiple connections enable, default is false.
     */
    private boolean multipleConnectionsEnable;

    public ConnectionBuilder() {
    }

    public ConnectionBuilder(String url) {
        this.url = url;
    }

    public static ConnectionBuilder getInstance() {
        return new ConnectionBuilder();
    }

    /**
     * establish a connection to the given database URL
     *
     * @return a connection to the URL
     * @throws Exception error occurs or the url is null or not start with `jdbc`
     */
    public Connection build() throws Exception {
        if (url == null || !url.startsWith("jdbc")) {
            throw new Exception("Connection url error, regular format like 'jdbc:guinsoo:mem:'");
        }

        String jdbcUrl;

        switch (parseEngine()) {
            case 1:
            case 2:
                Class.forName("org.guinsoo.Driver");
                jdbcUrl = url;
                break;
            case 3:
                Class.forName("org.guinsoodb.GuinsooDBDriver");
                jdbcUrl = "jdbc:guinsoodb:";
                break;
            default:
                throw new Exception("Database engine initialize failed");
        }

        return DriverManager.getConnection(jdbcUrl);
    }

    public String getUrl() {
        return url;
    }

    public ConnectionBuilder setUrl(String url) {
        this.url = url;
        return this;
    }

    private int parseEngine() {
        String key = "STORE";
        String pageStore = "1";
        String mvStore = "2";
        String quickStore = "3";
        Map<String, String> map = readSettingsFromURL(url.toUpperCase());
        if (map.size() == 0 || !map.containsKey(key)) {
            return -1;
        } else {
            String value = map.get(key);
            if (pageStore.equals(value)) {
                return 1;
            } else if (mvStore.equals(value)) {
                return 2;
            } else if (quickStore.equals(value)) {
                return 3;
            } else {
                return -1;
            }
        }
    }
}