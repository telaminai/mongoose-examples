/*
 * SPDX-FileCopyrightText: © 2025 Gregory Higgins <greg.higgins@v12technology.com>
 * SPDX-License-Identifier: AGPL-3.0-only
 */

package com.telamin.mongoose.example.service;

import com.fluxtion.runtime.lifecycle.Lifecycle;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.java.Log;

/**
 * A simple lifecycle-based service that provides greeting functionality.
 * This demonstrates how to create a basic service that can be injected
 * into other components and participate in the server lifecycle.
 */
@Log
public class GreetingService implements Lifecycle {

    @Getter
    @Setter
    private String prefix = "Hello";
    
    @Getter
    @Setter
    private String suffix = "!";

    public GreetingService() {
    }

    public GreetingService(String prefix, String suffix) {
        this.prefix = prefix;
        this.suffix = suffix;
    }

    @Override
    public void init() {
        log.info("GreetingService.init - initializing greeting service with prefix: " + prefix);
    }

    @Override
    public void start() {
        log.info("GreetingService.start - greeting service is ready");
    }

    @Override
    public void stop() {
        log.info("GreetingService.stop - stopping greeting service");
    }

    @Override
    public void tearDown() {
        log.info("GreetingService.tearDown - cleaning up greeting service");
        stop();
    }

    /**
     * Creates a greeting message for the given name.
     * 
     * @param name the name to greet
     * @return a formatted greeting message
     */
    public String greet(String name) {
        if (name == null || name.trim().isEmpty()) {
            name = "World";
        }
        return prefix + " " + name + suffix;
    }

    /**
     * Creates a personalized greeting with additional context.
     * 
     * @param name the name to greet
     * @param context additional context for the greeting
     * @return a formatted greeting message with context
     */
    public String greetWithContext(String name, String context) {
        String basicGreeting = greet(name);
        if (context != null && !context.trim().isEmpty()) {
            return basicGreeting + " " + context;
        }
        return basicGreeting;
    }
}