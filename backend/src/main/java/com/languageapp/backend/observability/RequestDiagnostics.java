package com.languageapp.backend.observability;

import org.slf4j.MDC;
import java.util.UUID;

public final class RequestDiagnostics {
    public static final String HEADER="X-Request-ID";
    static final String ATTRIBUTE=RequestDiagnostics.class.getName()+".id";
    private RequestDiagnostics() { }
    public static String currentId() { return valid(MDC.get("requestId")); }
    public static String valid(String value) {
        if(value==null)return null;
        try { var parsed=UUID.fromString(value);return parsed.toString().equals(value)?value:null; }
        catch(IllegalArgumentException invalid){return null;}
    }
    /** Async work carries only the server-generated correlation value, never arbitrary ambient MDC. */
    public static Runnable decorate(Runnable task) {
        String id=currentId();
        return ()->{var previous=MDC.getCopyOfContextMap();MDC.clear();
            if(id!=null)MDC.put("requestId",id);
            try{task.run();}finally{MDC.clear();if(previous!=null)MDC.setContextMap(previous);}
        };
    }
}
