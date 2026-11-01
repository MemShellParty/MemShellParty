package com.reajason.javaweb.packer.jar.agent.fileless;

import java.lang.instrument.Instrumentation;

public interface InstrumentationProvider {
    Instrumentation acquire() throws Exception;
}
