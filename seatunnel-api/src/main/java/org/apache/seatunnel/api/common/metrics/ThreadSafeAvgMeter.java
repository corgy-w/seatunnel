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

package org.apache.seatunnel.api.common.metrics;

import java.io.Serializable;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicLongFieldUpdater;

public class ThreadSafeAvgMeter implements Meter, Serializable {

    private static final long serialVersionUID = 1L;

    private static final AtomicLongFieldUpdater<ThreadSafeAvgMeter> VOLATILE_VALUE_UPDATER =
            AtomicLongFieldUpdater.newUpdater(ThreadSafeAvgMeter.class, "value");

    private final String name;

    private volatile long value;

    private final AtomicLong times;

    public ThreadSafeAvgMeter(String name) {
        this.name = name;
        this.times = new AtomicLong();
    }

    @Override
    public void markEvent() {
        times.incrementAndGet();
        VOLATILE_VALUE_UPDATER.incrementAndGet(this);
    }

    @Override
    public void markEvent(long n) {
        times.incrementAndGet();
        VOLATILE_VALUE_UPDATER.addAndGet(this, n);
    }

    @Override
    public double getRate() {
        return (double) value / times.get();
    }

    @Override
    public long getCount() {
        return VOLATILE_VALUE_UPDATER.get(this);
    }

    @Override
    public String name() {
        return name;
    }

    @Override
    public Unit unit() {
        return Unit.COUNT;
    }

    @Override
    public String toString() {
        return "ThreadSafeAvgMeter{"
                + "name='"
                + name
                + '\''
                + ", value="
                + value
                + ", times="
                + times
                + '}';
    }
}
