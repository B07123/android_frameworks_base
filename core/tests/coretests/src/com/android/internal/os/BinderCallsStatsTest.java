/*
 * Copyright (C) 2018 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License
 */

package com.android.internal.os;

import android.os.Binder;
import android.platform.test.annotations.Presubmit;
import android.support.test.filters.SmallTest;
import android.support.test.runner.AndroidJUnit4;
import android.util.SparseArray;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.assertEquals;

@SmallTest
@RunWith(AndroidJUnit4.class)
@Presubmit
public class BinderCallsStatsTest {
    public static final int TEST_UID = 1;

    @Test
    public void testDetailedOff() {
        TestBinderCallsStats bcs = new TestBinderCallsStats(false);
        Binder binder = new Binder();
        BinderCallsStats.CallSession callSession = bcs.callStarted(binder, 1);
        bcs.time += 10;
        bcs.callEnded(callSession);

        SparseArray<BinderCallsStats.UidEntry> uidEntries = bcs.getUidEntries();
        assertEquals(1, uidEntries.size());
        BinderCallsStats.UidEntry uidEntry = uidEntries.get(TEST_UID);
        Assert.assertNotNull(uidEntry);
        assertEquals(1, uidEntry.callCount);
        assertEquals(10, uidEntry.cpuTimeMicros);
        assertEquals("Detailed tracking off - no entries should be returned",
                0, uidEntry.getCallStatsList().size());

        BinderCallsStats.UidEntry sampledEntries = bcs.getSampledEntries();
        List<BinderCallsStats.CallStat> sampledCallStatsList = sampledEntries.getCallStatsList();
        assertEquals(1, sampledCallStatsList.size());


        assertEquals(1, sampledCallStatsList.get(0).callCount);
        assertEquals(10, sampledCallStatsList.get(0).cpuTimeMicros);
        assertEquals(binder.getClass().getName(), sampledCallStatsList.get(0).className);
        assertEquals(1, sampledCallStatsList.get(0).msg);

        callSession = bcs.callStarted(binder, 1);
        bcs.time += 20;
        bcs.callEnded(callSession);

        uidEntry = bcs.getUidEntries().get(TEST_UID);
        assertEquals(2, uidEntry.callCount);
        // When sampling is enabled, cpu time is only measured for the first transaction in the
        // sampling interval, for others an average duration of previous transactions is used as
        // approximation
        assertEquals(20, uidEntry.cpuTimeMicros);
        sampledCallStatsList = sampledEntries.getCallStatsList();
        assertEquals(1, sampledCallStatsList.size());

        callSession = bcs.callStarted(binder, 2);
        bcs.time += 50;
        bcs.callEnded(callSession);
        uidEntry = bcs.getUidEntries().get(TEST_UID);
        assertEquals(3, uidEntry.callCount);

        // This is the first transaction of a new type, so the real CPU time will be measured
        assertEquals(70, uidEntry.cpuTimeMicros);
        sampledCallStatsList = sampledEntries.getCallStatsList();
        assertEquals(2, sampledCallStatsList.size());
    }

    @Test
    public void testDetailedOn() {
        TestBinderCallsStats bcs = new TestBinderCallsStats(true);
        Binder binder = new Binder();
        BinderCallsStats.CallSession callSession = bcs.callStarted(binder, 1);
        bcs.time += 10;
        bcs.callEnded(callSession);

        SparseArray<BinderCallsStats.UidEntry> uidEntries = bcs.getUidEntries();
        assertEquals(1, uidEntries.size());
        BinderCallsStats.UidEntry uidEntry = uidEntries.get(TEST_UID);
        Assert.assertNotNull(uidEntry);
        assertEquals(1, uidEntry.callCount);
        assertEquals(10, uidEntry.cpuTimeMicros);
        assertEquals(1, uidEntry.getCallStatsList().size());

        BinderCallsStats.UidEntry sampledEntries = bcs.getSampledEntries();
        assertEquals("Sampling is not used when detailed tracking on",
                0, bcs.getSampledEntries().getCallStatsList().size());

        List<BinderCallsStats.CallStat> callStatsList = uidEntry.getCallStatsList();
        assertEquals(1, callStatsList.get(0).callCount);
        assertEquals(10, callStatsList.get(0).cpuTimeMicros);
        assertEquals(binder.getClass().getName(), callStatsList.get(0).className);
        assertEquals(1, callStatsList.get(0).msg);

        callSession = bcs.callStarted(binder, 1);
        bcs.time += 20;
        bcs.callEnded(callSession);

        uidEntry = bcs.getUidEntries().get(TEST_UID);
        assertEquals(2, uidEntry.callCount);
        assertEquals(30, uidEntry.cpuTimeMicros);
        callStatsList = uidEntry.getCallStatsList();
        assertEquals(1, callStatsList.size());

        callSession = bcs.callStarted(binder, 2);
        bcs.time += 50;
        bcs.callEnded(callSession);
        uidEntry = bcs.getUidEntries().get(TEST_UID);
        assertEquals(3, uidEntry.callCount);

        // This is the first transaction of a new type, so the real CPU time will be measured
        assertEquals(80, uidEntry.cpuTimeMicros);
        callStatsList = uidEntry.getCallStatsList();
        assertEquals(2, callStatsList.size());
    }

    @Test
    public void testGetHighestValues() {
        List<Integer> list = Arrays.asList(1, 2, 3, 4);
        List<Integer> highestValues = BinderCallsStats
                .getHighestValues(list, value -> value, 0.8);
        assertEquals(Arrays.asList(4, 3, 2), highestValues);
    }

    static class TestBinderCallsStats extends BinderCallsStats {
        int callingUid = TEST_UID;
        long time = 1234;

        TestBinderCallsStats(boolean detailedTracking) {
            super(detailedTracking);
        }

        @Override
        protected long getThreadTimeMicro() {
            return time;
        }

        @Override
        protected int getCallingUid() {
            return callingUid;
        }
    }

}
