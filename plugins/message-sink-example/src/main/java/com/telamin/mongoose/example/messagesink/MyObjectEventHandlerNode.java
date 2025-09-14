package com.telamin.mongoose.example.messagesink;

import com.fluxtion.runtime.annotations.runtime.ServiceRegistered;
import com.fluxtion.runtime.node.ObjectEventHandlerNode;
import com.fluxtion.runtime.output.MessageSink;

public class MyObjectEventHandlerNode extends ObjectEventHandlerNode {
    private MessageSink sink;

    @ServiceRegistered
    public void register(MessageSink sink, String name) {
        this.sink = sink;
    }

    @Override
    protected boolean handleEvent(Object event) {
        if (event instanceof String s) {
            // The handler receives the event and forwards it to the sink
            // In a real application, you might transform or enrich the event here
            if (sink != null) {
                sink.accept(s);
            }
            return true;
        }
        return true;
    }
}
