package com.telamin.mongoose.example.yaml.loader;

import com.fluxtion.dataflow.serverplugin.loader.yaml.EventHandlerLoader;
import com.fluxtion.dataflow.serverplugin.loader.yaml.EventHandlerLoader.EventLoadAtStartup;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventFeedConfig;
import com.telamin.mongoose.config.EventProcessorConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.config.ServiceConfig;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

import java.io.File;
import java.net.URL;
import java.util.Set;

/**
 * Example demonstrating how to use the YAML Service Loader plugin to load
 * event processors from a YAML configuration file.
 */
public class YamlServiceLoaderExample {

    public static void main(String[] args) throws InterruptedException {
        // 1. Locate the YAML configuration file
        URL yamlUrl = YamlServiceLoaderExample.class.getResource("/yaml/log-processor.yaml");
        if (yamlUrl == null) {
            throw new RuntimeException("Could not find /yaml/log-processor.yaml");
        }
        String yamlPath = new File(yamlUrl.getPath()).getAbsolutePath();
        System.out.println("Using YAML config from: " + yamlPath);

        // 2. Configure the YAML loader service
        EventHandlerLoader yamlLoader = new EventHandlerLoader();
        EventLoadAtStartup loadConfig = new EventLoadAtStartup();
        loadConfig.setYamlFile(yamlPath);
        loadConfig.setCompile(true);
        yamlLoader.setLoadAtStartup(Set.of(loadConfig));

        // 3. Setup a simple in-memory feed to send events to
        InMemoryEventSource<String> eventSource = new InMemoryEventSource<>();
        EventFeedConfig<?> feedConfig = EventFeedConfig.builder()
                .instance(eventSource)
                .name("yamlLoader") // This name matches the default group in yamlLoader if not specified
                .agent("producer-agent", new SleepingMillisIdleStrategy(1))
                .broadcast(true)
                .build();

        // 4. Build and boot the Mongoose server
        ServiceConfig<EventHandlerLoader> yamlLoaderConfig = ServiceConfig.<EventHandlerLoader>builder()
                .service(yamlLoader)
                .serviceClass(EventHandlerLoader.class)
                .name("yamlLoaderService")
                .build();

        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                .addEventFeed(feedConfig)
                .addService(yamlLoaderConfig)
                .build();

        MongooseServer server = MongooseServer.bootServer(serverConfig);

        try {
            System.out.println("Server booted. Sending events...");
            Thread.sleep(2000);
            for (int i = 0; i < 5; i++) {
                eventSource.offer("Event #" + i);
                Thread.sleep(200);
            }
            Thread.sleep(1000);
        } finally {
            server.stop();
        }
    }
}
