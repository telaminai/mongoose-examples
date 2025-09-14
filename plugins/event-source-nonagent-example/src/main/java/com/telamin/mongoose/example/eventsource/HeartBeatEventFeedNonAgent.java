/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.eventsource;

import com.fluxtion.runtime.annotations.Start;
import com.telamin.mongoose.service.extension.AbstractEventSourceService;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

public class HeartBeatEventFeedNonAgent extends AbstractEventSourceService<HeartbeatEvent> {

    private final HeartbeatEvent heartbeatEvent = new HeartbeatEvent();

    public HeartBeatEventFeedNonAgent() {
        super("HeartBeatService-NonAgent");
    }

    @Override
    public void start() {
        ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
        executorService.scheduleAtFixedRate(this::heartbeat, 1, 1, TimeUnit.SECONDS);
    }

    private void heartbeat() {
        heartbeatEvent.setTimestamp(System.nanoTime());
        System.out.println("publish " +heartbeatEvent);
        output.publish(heartbeatEvent);
    }
}