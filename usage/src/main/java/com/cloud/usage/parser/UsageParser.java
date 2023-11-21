// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// the License.  You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.
package com.cloud.usage.parser;

import java.util.Date;

import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.LogManager;

import org.apache.cloudstack.managed.context.ManagedContextRunnable;

public abstract class UsageParser extends ManagedContextRunnable {
    protected static Logger s_logger = LogManager.getLogger(UsageParser.class.getName());

    @Override
    protected void runInContext() {
        try {
            parse(null);
        } catch (Exception e) {
            s_logger.warn("Error while parsing usage events", e);
        }
    }

    public abstract void parse(Date endDate);
}
