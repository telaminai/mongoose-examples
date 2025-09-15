package com.telamin.mongoose.example.howto;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.lifecycle.Lifecycle;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.runtime.output.MessageSink;
import com.telamin.mongoose.MongooseServer;
import com.telamin.mongoose.config.*;
import com.telamin.mongoose.connector.memory.InMemoryEventSource;
import com.telamin.mongoose.connector.memory.InMemoryMessageSink;
import com.telamin.mongoose.service.admin.AdminCommandRegistry;
import com.telamin.mongoose.service.admin.AdminCommandRequest;
import com.telamin.mongoose.service.admin.impl.AdminCommandProcessor;
import com.telamin.mongoose.service.admin.impl.CliAdminCommandProcessor;
import com.telamin.mongoose.service.servercontrol.MongooseServerAdmin;
import com.fluxtion.runtime.service.Service;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/**
 * Example demonstrating how to write and use admin commands in Mongoose server.
 * 
 * This example shows:
 * - How to register admin commands from processors and services
 * - How to wire admin infrastructure in MongooseServerConfig
 * - How to invoke commands programmatically
 * - Different types of admin commands (ping, echo, time, cache operations)
 */
public class WritingAnAdminCommandExample {

    public static void main(String[] args) throws Exception {
        System.out.println("=== Writing an Admin Command Example ===");

        // Create event source and sink for the example
        InMemoryEventSource<Object> eventSource = new InMemoryEventSource<>();
        InMemoryMessageSink sink = new InMemoryMessageSink();

        // Build the Mongoose server configuration with admin infrastructure
        MongooseServerConfig config = buildServerConfig(eventSource, sink);

        // Start the server
        MongooseServer server = MongooseServer.bootServer(config);

        System.out.println("Server started with admin command support");

        // Demonstrate admin command usage
        demonstrateAdminCommands(server);

        // Send some events to trigger processor activity
        demonstrateProcessorActivity(eventSource);

        // Wait a bit for processing
        Thread.sleep(1000);

        // Show final results
        displayResults(sink);

        // Stop the server
        server.stop();
        System.out.println("Server stopped");
    }

    private static MongooseServerConfig buildServerConfig(InMemoryEventSource<Object> eventSource, 
                                                         InMemoryMessageSink sink) {
        // Create event feed configuration
        EventFeedConfig<?> eventFeed = EventFeedConfig.builder()
                .instance(eventSource)
                .name("exampleFeed")
                .broadcast(true)
                .build();

        // Create processor with admin command support
        AdminCommandExampleProcessor processor = new AdminCommandExampleProcessor();

        EventProcessorGroupConfig processorGroup = EventProcessorGroupConfig.builder()
                .agentName("processor-agent")
                .put("admin-processor", new EventProcessorConfig(processor))
                .build();

        // Create event sink configuration
        EventSinkConfig<?> sinkConfig = EventSinkConfig.builder()
                .instance(sink)
                .name("exampleSink")
                .build();

        // Admin registry/dispatcher service
        ServiceConfig<AdminCommandRegistry> adminSvc = ServiceConfig.<AdminCommandRegistry>builder()
                .service(new AdminCommandProcessor())
                .serviceClass(AdminCommandRegistry.class)
                .name("adminService")
                .build();

        // Optional: interactive CLI (commented out for this example)
        // ServiceConfig<?> cliSvc = ServiceConfig.builder()
        //         .service(new CliAdminCommandProcessor())
        //         .name("adminCli")
        //         .build();

        // Server admin commands (list services/processors, stop processors)
        ServiceConfig<?> serverAdmin = ServiceConfig.builder()
                .service(new MongooseServerAdmin())
                .name("serverAdmin")
                .build();

        // Custom service with admin commands
        ServiceConfig<?> customService = ServiceConfig.builder()
                .service(new CustomAdminService())
                .name("customService")
                .build();

        return MongooseServerConfig.builder()
                .addEventFeed(eventFeed)
                .addProcessorGroup(processorGroup)
                .addEventSink(sinkConfig)
                .addService(adminSvc)
                .addService(serverAdmin)
                .addService(customService)
                // .addService(cliSvc)  // Uncomment to enable CLI
                .build();
    }

    private static void demonstrateAdminCommands(MongooseServer server) throws Exception {
        System.out.println("\n=== Demonstrating Admin Commands ===");

        // Get the admin registry
        Service<?> svc = server.registeredServices().get("adminService");
        AdminCommandRegistry registry = (AdminCommandRegistry) svc.instance();

        // Test built-in commands
        System.out.println("\n1. Testing built-in 'commands' command:");
        executeCommand(registry, "commands", List.of());

        System.out.println("\n2. Testing built-in 'help' command:");
        executeCommand(registry, "help", List.of());

        // Test processor commands
        System.out.println("\n3. Testing processor admin commands:");
        executeCommand(registry, "processor.ping", List.of());
        executeCommand(registry, "processor.echo", List.of("Hello", "from", "admin", "command"));
        executeCommand(registry, "processor.status", List.of());

        // Test service commands
        System.out.println("\n4. Testing service admin commands:");
        executeCommand(registry, "service.time", List.of());
        executeCommand(registry, "service.cache.size", List.of());
        executeCommand(registry, "service.cache.clear", List.of());
        executeCommand(registry, "service.cache.size", List.of());

        // Test server admin commands
        System.out.println("\n5. Testing server admin commands:");
        executeCommand(registry, "server.services.list", List.of());
        executeCommand(registry, "server.processors.list", List.of());
    }

    private static void executeCommand(AdminCommandRegistry registry, String command, List<String> args) {
        System.out.println("Executing: " + command + " " + String.join(" ", args));

        AdminCommandRequest request = new AdminCommandRequest();
        request.setCommand(command);
        request.setArguments(args);
        request.setOutput(result -> System.out.println("  Output: " + result));
        request.setErrOutput(error -> System.err.println("  Error: " + error));

        registry.processAdminCommandRequest(request);
    }

    private static void demonstrateProcessorActivity(InMemoryEventSource<Object> eventSource) {
        System.out.println("\n=== Sending Events to Processor ===");

        eventSource.publishNow("Hello World");
        eventSource.publishNow(42);
        eventSource.publishNow("Admin Command Example");
        eventSource.publishNow(3.14);

        System.out.println("Sent 4 events to processor");
    }

    private static void displayResults(InMemoryMessageSink sink) {
        System.out.println("\n=== Processing Results ===");
        System.out.println("Messages received by sink: " + sink.getMessages().size());
        sink.getMessages().forEach(msg -> System.out.println("  " + msg));
    }

    /**
     * Example processor that registers admin commands
     */
    public static class AdminCommandExampleProcessor extends ObjectEventHandlerNode {
        private MessageSink<String> sink;
        private int processedCount = 0;
        private String lastEvent = "none";

        @ServiceRegistered
        public void wire(MessageSink<String> sink, String name) {
            this.sink = sink;
        }

        @ServiceRegistered
        public void registerAdmin(AdminCommandRegistry admin, String name) {
            System.out.println("Registering admin commands for processor: " + name);
            admin.registerCommand("processor.ping", this::ping);
            admin.registerCommand("processor.echo", this::echo);
            admin.registerCommand("processor.status", this::status);
        }

        @Override
        public boolean handleEvent(Object event) {
            if (sink == null || event == null) {
                return true;
            }
            processedCount++;
            lastEvent = event.toString();
            sink.accept("Processed: " + event + " (count: " + processedCount + ")");
            return true;
        }

        private void ping(List<String> args, Consumer<Object> out, Consumer<Object> err) {
            out.accept("pong from processor");
        }

        private void echo(List<String> args, Consumer<Object> out, Consumer<Object> err) {
            if (args.size() <= 1) {
                err.accept("echo command requires arguments");
                return;
            }
            // args[0] is the command name, args[1..] are user args
            String message = String.join(" ", args.subList(1, args.size()));
            out.accept("Echo from processor: " + message);
        }

        private void status(List<String> args, Consumer<Object> out, Consumer<Object> err) {
            out.accept("Processor status - Events processed: " + processedCount + 
                      ", Last event: " + lastEvent);
        }
    }

    /**
     * Example service that registers admin commands
     */
    public static class CustomAdminService implements Lifecycle {
        private AdminCommandRegistry registry;
        private int cacheSize = 0;

        @ServiceRegistered
        public void admin(AdminCommandRegistry registry) { 
            this.registry = registry; 
        }

        @Override
        public void start() {
            System.out.println("Registering admin commands for custom service");
            registry.registerCommand("service.time", this::getCurrentTime);
            registry.registerCommand("service.cache.size", this::getCacheSize);
            registry.registerCommand("service.cache.clear", this::clearCache);
            registry.registerCommand("service.cache.add", this::addToCache);

            // Initialize some cache data
            cacheSize = 5;
        }

        private void getCurrentTime(List<String> args, Consumer<Object> out, Consumer<Object> err) {
            out.accept("Current time: " + Instant.now().toString());
        }

        private void getCacheSize(List<String> args, Consumer<Object> out, Consumer<Object> err) {
            out.accept("Cache size: " + cacheSize);
        }

        private void clearCache(List<String> args, Consumer<Object> out, Consumer<Object> err) {
            cacheSize = 0;
            out.accept("Cache cleared");
        }

        private void addToCache(List<String> args, Consumer<Object> out, Consumer<Object> err) {
            if (args.size() < 2) {
                err.accept("add command requires a count argument");
                return;
            }

            try {
                int count = Integer.parseInt(args.get(1));
                cacheSize += count;
                out.accept("Added " + count + " items to cache. New size: " + cacheSize);
            } catch (NumberFormatException e) {
                err.accept("Invalid count argument: " + args.get(1));
            }
        }

        @Override public void init() {}
        @Override public void stop() {}
        @Override public void tearDown() {}
    }
}
