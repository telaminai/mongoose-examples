/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.messagesink;

import com.telamin.fluxtion.runtime.lifecycle.Lifecycle;
import com.telamin.fluxtion.runtime.output.AbstractMessageSink;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * A message sink that formats and prints messages to the console with timestamps
 * and configurable formatting options.
 * <p>
 * This example demonstrates:
 * <ul>
 *   <li>Extending AbstractMessageSink to create a custom sink</li>
 *   <li>Implementing the Lifecycle interface for proper resource management</li>
 *   <li>Using configurable properties to customize behavior</li>
 *   <li>Formatting messages before output</li>
 * </ul>
 */
@Log
public class ConsoleMessageSink extends AbstractMessageSink<Object> implements Lifecycle {

    @Getter
    @Setter
    private String prefix = "EVENT";

    @Getter
    @Setter
    private boolean includeTimestamp = true;

    @Getter
    @Setter
    private String timestampFormat = "yyyy-MM-dd HH:mm:ss.SSS";

    private DateTimeFormatter formatter;

    @Override
    public void init() {
        // Lightweight initialization
        log.info("Initializing ConsoleMessageSink");
    }

    @Override
    public void start() {
        // Initialize the formatter with the configured format
        formatter = DateTimeFormatter.ofPattern(timestampFormat);
        log.info("Started ConsoleMessageSink with prefix: " + prefix + 
                 ", includeTimestamp: " + includeTimestamp + 
                 ", timestampFormat: " + timestampFormat);
    }

    @Override
    protected void sendToSink(Object value) {
        if (value == null) {
            return;
        }

        StringBuilder sb = new StringBuilder();
        
        // Add timestamp if configured
        if (includeTimestamp) {
            sb.append("[").append(LocalDateTime.now().format(formatter)).append("] ");
        }
        
        // Add prefix
        sb.append(prefix).append(": ");
        
        // Add the actual message
        sb.append(value);
        
        // Print to console
        System.out.println(sb);
    }

    @Override
    public void stop() {
        log.info("Stopping ConsoleMessageSink");
        // Flush System.out to ensure all messages are printed
        System.out.flush();
    }

    @Override
    public void tearDown() {
        log.info("Tearing down ConsoleMessageSink");
        stop();
    }
}