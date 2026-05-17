package com.telamin.mongoose.example.yaml.loader;

import com.telamin.fluxtion.runtime.node.ObjectEventHandlerNode;
import lombok.extern.log4j.Log4j2;

/**
 * A simple event handler that logs received events.
 */
@Log4j2
public class YamlLogHandler extends ObjectEventHandlerNode {

    private String prefix = "YAML-EVENT: ";

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    @Override
    protected boolean handleEvent(Object event) {
        String msg = prefix + " received event: " + event;
        System.out.println(msg);
        log.info(msg);
        return true;
    }
}
