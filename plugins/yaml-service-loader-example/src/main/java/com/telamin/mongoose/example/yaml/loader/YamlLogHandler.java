package com.telamin.mongoose.example.yaml.loader;

import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;

/**
 * Event handler instantiated from log-processor.yaml by svc-loader-yaml.
 *
 * <p>{@code prefix} is injected via SnakeYAML bean-setter — the YAML's
 * {@code prefix: "..."} entry under the {@code logHandler} node ends up here.
 */
public class YamlLogHandler extends ObjectEventHandlerNode {

    public static final String FEED_NAME = "yamlLoader";

    private String prefix = "YAML-LOADED-HANDLER:";

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    protected boolean handleEvent(Object event) {
        System.out.println(prefix + " " + event);
        return true;
    }
}
