package com.telamin.mongoose.example.yaml.loader;

import com.telamin.mongoose.plugin.loader.yaml.EventHandlerLoader;
import com.telamin.mongoose.plugin.loader.yaml.EventHandlerLoader.EventLoadAtStartup;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.EventFeedConfig;
import com.telamin.mongoose.config.MongooseServerConfig;
import com.telamin.mongoose.config.ServiceConfig;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import org.agrona.concurrent.SleepingMillisIdleStrategy;

import java.net.URISyntaxException;
import java.net.URL;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Set;

/**
 * Boots a Mongoose server that loads an event-processor topology from a YAML
 * file using svc-loader-yaml, then publishes a few events from an in-memory
 * feed so the processor (a {@link YamlLogHandler}) can log them.
 *
 * <p>The feed is named {@code yamlLoader} to match the loader's default
 * processor group; with {@code broadcast=true} it would reach every group
 * regardless, but matching the name keeps the wiring obvious.
 */
public class YamlServiceLoaderExample {

    private static final String YAML_RESOURCE = "/yaml/log-processor.yaml";

    public static void main(String[] args) throws InterruptedException, URISyntaxException {
        Path yamlPath = locateYaml();
        System.out.println("Using YAML config from: " + yamlPath);

        EventLoadAtStartup loadConfig = new EventLoadAtStartup();
        loadConfig.setYamlFile(yamlPath.toString());
        loadConfig.setCompile(true);

        EventHandlerLoader yamlLoader = new EventHandlerLoader();
        yamlLoader.setLoadAtStartup(Set.of(loadConfig));

        InMemoryEventSource<String> eventSource = new InMemoryEventSource<>();
        EventFeedConfig<?> feedConfig = EventFeedConfig.builder()
                .instance(eventSource)
                .name("yamlLoader")
                .agent("producer-agent", new SleepingMillisIdleStrategy(1))
                .broadcast(true)
                .build();

        ServiceConfig<EventHandlerLoader> loaderService = ServiceConfig.<EventHandlerLoader>builder()
                .service(yamlLoader)
                .serviceClass(EventHandlerLoader.class)
                .name("yamlLoaderService")
                .build();

        MongooseServerConfig serverConfig = MongooseServerConfig.builder()
                .addEventFeed(feedConfig)
                .addService(loaderService)
                .build();

        MongooseServer server = MongooseServer.bootServer(serverConfig);
        try {
            Thread.sleep(1_500); // give the loader time to install the processor
            System.out.println("Sending events...");
            for (int i = 0; i < 5; i++) {
                eventSource.offer("Event #" + i);
                Thread.sleep(200);
            }
            Thread.sleep(500);
        } finally {
            server.stop();
        }
    }

    private static Path locateYaml() throws URISyntaxException {
        URL url = YamlServiceLoaderExample.class.getResource(YAML_RESOURCE);
        if (url == null) {
            throw new IllegalStateException("Could not find classpath resource " + YAML_RESOURCE);
        }
        // toURI handles spaces and non-ASCII characters correctly; new File(url.getPath()) does not.
        return Paths.get(url.toURI());
    }
}
