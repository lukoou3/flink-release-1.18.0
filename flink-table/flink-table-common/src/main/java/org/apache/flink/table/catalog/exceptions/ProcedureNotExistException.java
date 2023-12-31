/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.flink.table.catalog.exceptions;

import org.apache.flink.annotation.PublicEvolving;
import org.apache.flink.table.catalog.ObjectPath;

/** Exception for trying to operate on a procedure that doesn't exist. */
@PublicEvolving
public class ProcedureNotExistException extends Exception {

    private static final String MSG = "Procedure %s does not exist in Catalog %s.";

    public ProcedureNotExistException(String catalogName, ObjectPath procedurePath) {
        this(catalogName, procedurePath, null);
    }

    public ProcedureNotExistException(
            String catalogName, ObjectPath procedurePath, Throwable cause) {
        super(String.format(MSG, procedurePath.getFullName(), catalogName), cause);
    }
}
