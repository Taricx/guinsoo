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

package org.guinsoo.quickstore.common.integer;

import org.guinsoo.quickstore.StoreScopedReadBuffer;
import org.guinsoo.quickstore.StoreSerializer;

/**
 * StoreIntSerializer
 *
 * @author cius.ji
 * @since 1.8+
 */
public class StoreIntSerializer implements StoreSerializer<Integer> {

    private final int size;

    public StoreIntSerializer() {
        this(Integer.BYTES);
    };

    public StoreIntSerializer(int size) {
        this.size = size;
    }

    @Override
    public void serialize(Integer object, StoreScopedReadBuffer targetBuffer) {
        // TODO: write buffer
    }

    @Override
    public Integer deserialize(StoreScopedReadBuffer byteBuffer) {
        // TODO: get buffer
        return null;
    }

    @Override
    public int calculateSize(Integer object) {
        return size;
    }
}
