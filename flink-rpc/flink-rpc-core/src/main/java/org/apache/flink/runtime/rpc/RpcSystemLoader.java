/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.runtime.rpc;

import org.apache.flink.configuration.Configuration;

/** A loader for an {@link RpcSystem}. */
public interface RpcSystemLoader {
    /**
     * Returns the loading priority for this loader, for a deterministic loading order if multiple
     * rpc system loaders are present on the classpath. {@code 0} designates the highest priority.
     */
    int getLoadPriority();

    RpcSystem loadRpcSystem(Configuration config);
}
