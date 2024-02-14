// Licensed to the Apache Software Foundation (ASF) under one
// or more contributor license agreements.  See the NOTICE file
// distributed with this work for additional information
// regarding copyright ownership.  The ASF licenses this file
// to you under the Apache License, Version 2.0 (the
// "License"); you may not use this file except in compliance
// with the License.  You may obtain a copy of the License at
//
//   http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

package com.cloud.security;

import java.util.Date;
import java.util.UUID;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.GeneratedValue;
import javax.persistence.GenerationType;
import javax.persistence.Id;
import javax.persistence.Table;
import javax.persistence.Temporal;
import javax.persistence.TemporalType;

@Entity
@Table(name = "integrity_verification_initial_hash_final_result")
public class IntegrityVerificationFinalResultVO implements IntegrityVerificationFinalResult {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private long id;

    @Column(name = "uuid")
    private String uuid;

    @Column(name = "mshost_id")
    private long msHostId;

    @Column(name = "verification_final_result")
    private boolean verificationFinalResult;

    @Temporal(TemporalType.TIMESTAMP)
    @Column(name = "verification_date", updatable = true, nullable = true)
    private Date verificationDate;

    @Column(name = "verification_failed_list", length = 16777215)
    private String verificationFailedList;

    @Column(name = "type")
    private String type;

    public long getId() {
        return id;
    }

    @Override
    public String getUuid() {
        return uuid;
    }

    @Override
    public long getMsHostId() {
        return msHostId;
    }

    @Override
    public boolean getVerificationFinalResult() {
        return verificationFinalResult;
    }

    public String getVerificationFailedList() {
        return verificationFailedList;
    }

    @Override
    public Date getVerificationDate() { return verificationDate; }

    public String getType() { return type; }

    public void setUuid(String uuid) { this.uuid = uuid; }
    public void setVerificationFinalResult(boolean verificationFinalResult) { this.verificationFinalResult = verificationFinalResult; }

    public void setVerificationFailedList(String verificationFailedList) { this.verificationFailedList = verificationFailedList; }

    public void setVerificationDate(Date verificationDate) {
        this.verificationDate = verificationDate;
    }

    public void setType(String type) {
        this.type = type;
    }

    protected IntegrityVerificationFinalResultVO()  {
        this.uuid = UUID.randomUUID().toString();
    }

    public IntegrityVerificationFinalResultVO(long mshostId, boolean verificationFinalResult, String verificationFailedList, String type) {
        this.uuid = UUID.randomUUID().toString();
        this.msHostId = mshostId;
        this.verificationFinalResult = verificationFinalResult;
        this.verificationFailedList = verificationFailedList;
        this.type = type;
    }

    @Override
    public String toString() {
        return super.toString() +
                ", verification final result: " + verificationFinalResult +
                ", verification date: " + verificationDate +
                ", verification failed json: " + verificationFailedList;
    }
}
