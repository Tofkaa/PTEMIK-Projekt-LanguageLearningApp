package com.languageapp.backend.observability;

import org.slf4j.Logger;

/** Diagnostic structure only: exception messages, causes, bodies and identifiers are never formatted. */
public final class SafeDiagnostics {
    private SafeDiagnostics() { }
    public static void failure(Logger logger,String event,Throwable error) {
        logger.error("event={} type={} location={}", event(event),type(error),location(error));
    }
    public static String event(String value) {
        return value!=null && value.matches("[a-z][a-z0-9_.-]{0,79}")?value:"diagnostic.failure";
    }
    private static String type(Throwable error) {
        String name=error==null?"Unknown":error.getClass().getSimpleName();
        return name.matches("[A-Za-z][A-Za-z0-9_]{0,79}")?name:"Unknown";
    }
    private static String location(Throwable error) {
        if(error==null)return "unknown";
        for(var frame:error.getStackTrace()) {
            String name=frame.getClassName(),method=frame.getMethodName();
            if(name.startsWith("com.languageapp.backend.") && name.matches("[A-Za-z0-9_.$]{1,180}") && method.matches("[A-Za-z0-9_$<>]{1,80}"))
                return name+"."+method+":"+Math.max(0,frame.getLineNumber());
        }
        return "external";
    }
}
