/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.service;

import com.fluxtion.agrona.concurrent.Agent;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.util.concurrent.atomic.AtomicLong;

/**
 * A worker service that runs on its own agent thread and performs periodic work.
 * This demonstrates how to create an agent-hosted service that can perform
 * background tasks while participating in the server lifecycle.
 */
@Log
public class MetricsCollectorService implements Agent, Lifecycle {

    @Getter
    @Setter
    private long intervalNanos = 5_000_000_000L; // 5 seconds

    private volatile boolean running;
    private long lastRunNanos;
    
    // Simple metrics
    private final AtomicLong totalWorkCycles = new AtomicLong(0);
    private final AtomicLong totalMessages = new AtomicLong(0);
    private long startTime;

    public MetricsCollectorService() {
    }

    public MetricsCollectorService(long intervalNanos) {
        this.intervalNanos = intervalNanos;
    }

    @Override
    public void init() {
        log.info("MetricsCollectorService.init - initializing metrics collector");
        startTime = System.currentTimeMillis();
    }

    @Override
    public void start() {
        log.info("MetricsCollectorService.start - starting metrics collection");
        running = true;
        lastRunNanos = System.nanoTime();
    }

    @Override
    public int doWork() throws Exception {
        if (!running) {
            return 0;
        }

        long now = System.nanoTime();
        if (now - lastRunNanos >= intervalNanos) {
            lastRunNanos = now;
            
            // Perform periodic work - collect and log metrics
            long cycles = totalWorkCycles.incrementAndGet();
            long messages = totalMessages.get();
            long uptimeSeconds = (System.currentTimeMillis() - startTime) / 1000;
            
            log.info(String.format("Metrics Report - Cycles: %d, Messages: %d, Uptime: %ds", 
                    cycles, messages, uptimeSeconds));
            
            return 1; // Work was done
        }
        
        return 0; // No work done
    }

    @Override
    public String roleName() {
        return "MetricsCollectorService";
    }

    @Override
    public void stop() {
        log.info("MetricsCollectorService.stop - stopping metrics collection");
        running = false;
    }

    @Override
    public void tearDown() {
        log.info("MetricsCollectorService.tearDown - cleaning up metrics collector");
        stop();
    }

    /**
     * Records a message being processed (can be called by other components).
     */
    public void recordMessage() {
        totalMessages.incrementAndGet();
    }

    /**
     * Records multiple messages being processed.
     */
    public void recordMessages(long count) {
        totalMessages.addAndGet(count);
    }

    /**
     * Gets the current message count.
     */
    public long getMessageCount() {
        return totalMessages.get();
    }

    /**
     * Gets the current work cycle count.
     */
    public long getWorkCycleCount() {
        return totalWorkCycles.get();
    }

    /**
     * Gets the uptime in seconds.
     */
    public long getUptimeSeconds() {
        return (System.currentTimeMillis() - startTime) / 1000;
    }

    /**
     * Resets all metrics counters.
     */
    public void resetMetrics() {
        totalWorkCycles.set(0);
        totalMessages.set(0);
        startTime = System.currentTimeMillis();
        log.info("MetricsCollectorService - metrics reset");
    }
}