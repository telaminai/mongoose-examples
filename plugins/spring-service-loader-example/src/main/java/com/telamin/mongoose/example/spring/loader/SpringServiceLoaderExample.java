package com.telamin.mongoose.example.spring.loader;

import com.telamin.mongoose.plugin.loader.spring.SpringEventHandlerLoader;
import com.telamin.mongoose.plugin.loader.spring.SpringEventHandlerLoader.EventSpringFile;
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
 * Boots a Mongoose server that loads an event-processor topology from a Spring
 * XML bean graph using svc-loader-spring, then publishes a few events from an
 * in-memory feed so the loaded processor (a {@link SpringLogHandler}) can log
 * them.
 *
 * <p>The feed is named {@code springLoader} to match the loader's default
 * processor group; with {@code broadcast=true} it would reach every group
 * regardless, but matching the name keeps the wiring obvious.
 */
public class SpringServiceLoaderExample {

    private static final String SPRING_RESOURCE = "/spring/log-processor.xml";

    public static void main(String[] args) throws InterruptedException, URISyntaxException {
        Path springPath = locateXml();
        System.out.println("Using Spring XML from: " + springPath);

        EventSpringFile loadConfig = new EventSpringFile();
        loadConfig.setSpringFile(springPath.toString());
        loadConfig.setCompile(true);
        loadConfig.setAddEventAuditor(false);

        SpringEventHandlerLoader springLoader = new SpringEventHandlerLoader();
        springLoader.setLoadAtStartup(Set.of(loadConfig));

        InMemoryEventSource<String> eventSource = new InMemoryEventSource<>();
        EventFeedConfig<?> feedConfig = EventFeedConfig.builder()
                .instance(eventSource)
                .name("springLoader")
                .agent("producer-agent", new SleepingMillisIdleStrategy(1))
                .broadcast(true)
                .build();

        ServiceConfig<SpringEventHandlerLoader> loaderService = ServiceConfig.<SpringEventHandlerLoader>builder()
                .service(springLoader)
                .serviceClass(SpringEventHandlerLoader.class)
                .name("springLoaderService")
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

    private static Path locateXml() throws URISyntaxException {
        URL url = SpringServiceLoaderExample.class.getResource(SPRING_RESOURCE);
        if (url == null) {
            throw new IllegalStateException("Could not find classpath resource " + SPRING_RESOURCE);
        }
        return Paths.get(url.toURI());
    }
}
