/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.service;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import lombok.extern.java.Log;

/**
 * An event handler that demonstrates how to inject and use custom services
 * using the @ServiceRegistered annotation. This handler processes string events
 * and uses both the GreetingService and MetricsCollectorService.
 */
@Log
public class ServiceAwareEventHandler extends ObjectEventHandlerNode {

    private GreetingService greetingService;
    private MetricsCollectorService metricsService;

    /**
     * Injects the GreetingService when it becomes available.
     */
    @ServiceRegistered
    public void wireGreetingService(GreetingService greetingService, String name) {
        log.info("ServiceAwareEventHandler - injected GreetingService: " + name);
        this.greetingService = greetingService;
    }

    /**
     * Injects the MetricsCollectorService when it becomes available.
     */
    @ServiceRegistered
    public void wireMetricsService(MetricsCollectorService metricsService, String name) {
        log.info("ServiceAwareEventHandler - injected MetricsCollectorService: " + name);
        this.metricsService = metricsService;
    }

    @Override
    public void start() {
        log.info("ServiceAwareEventHandler - starting event handler");
        super.start();
    }

    @Override
    protected boolean handleEvent(Object event) {
        if (event instanceof String message) {
            // Record the message in metrics
            if (metricsService != null) {
                metricsService.recordMessage();
            }

            // Create a greeting using the greeting service
            String greeting = null;
            if (greetingService != null) {
                greeting = greetingService.greetWithContext(message, "from Mongoose!");
            } else {
                greeting = "Hello " + message + " (service not available)";
            }

            // Log the processed event
            log.info("Processed event: " + greeting);
            
            // Print to console for demo purposes
            System.out.println("Event processed: " + greeting);

            return true;
        }

        // Handle other event types if needed
        log.fine("Ignoring non-string event: " + event);
        return true;
    }

    @Override
    public void stop() {
        log.info("ServiceAwareEventHandler - stopping event handler");
        
        // Print final metrics if available
        if (metricsService != null) {
            log.info("Final metrics - Messages: " + metricsService.getMessageCount() + 
                    ", Uptime: " + metricsService.getUptimeSeconds() + "s");
        }
        
        super.stop();
    }

    /**
     * Gets the current message count from the metrics service.
     */
    public long getProcessedMessageCount() {
        return metricsService != null ? metricsService.getMessageCount() : 0;
    }

    /**
     * Checks if both services are available.
     */
    public boolean areServicesAvailable() {
        return greetingService != null && metricsService != null;
    }
}