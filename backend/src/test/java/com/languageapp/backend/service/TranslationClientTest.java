package com.languageapp.backend.service;

import com.languageapp.backend.exception.TranslationUnavailableException;
import com.languageapp.backend.exception.TooManyRequestsException;
import com.languageapp.backend.security.RequestBudgetStore;
import static org.mockito.Mockito.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import static org.junit.jupiter.api.Assertions.*;

class TranslationClientTest {
    HttpServer server;
    ExecutorService providerThreads;
    TranslationClient client;
    RequestBudgetStore budgets=mock(RequestBudgetStore.class);
    AtomicInteger calls=new AtomicInteger(),active=new AtomicInteger(),maximum=new AtomicInteger();
    AtomicReference<String> body=new AtomicReference<>("{\"responseStatus\":200,\"responseData\":{\"translatedText\":\"alma\"}}");
    AtomicInteger status=new AtomicInteger(200);
    AtomicReference<String> query=new AtomicReference<>();
    volatile long delay;
    @BeforeEach void setup()throws Exception {
        server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);providerThreads=Executors.newFixedThreadPool(8);server.setExecutor(providerThreads);
        server.createContext("/get",exchange->{
            query.set(exchange.getRequestURI().getRawQuery());
            calls.incrementAndGet();int now=active.incrementAndGet();maximum.accumulateAndGet(now,Math::max);
            try {
                try{Thread.sleep(delay);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();}
                byte[] bytes=body.get().getBytes(StandardCharsets.UTF_8);exchange.sendResponseHeaders(status.get(),bytes.length);exchange.getResponseBody().write(bytes);
            }catch(java.io.IOException cancelled){/* Expected cancellation by bounded clients. */}
            finally{active.decrementAndGet();exchange.close();}
        });server.start();client=new TranslationClient(Clock.systemUTC(),"http://127.0.0.1:"+server.getAddress().getPort()+"/get",500,budgets);
    }
    @AfterEach void stop(){client.close();server.stop(0);providerThreads.shutdownNow();}
    @Test void successfulPublicTranslationsAreCachedAndDeduplicated() {
        assertEquals("alma",client.translateAll("en",List.of("apple","apple")).get("apple"));
        assertEquals("alma",client.translateAll("en",List.of("apple")).get("apple"));assertEquals(1,calls.get());
        verify(budgets,times(1)).check(anyList());
        client.translateAll("de",List.of("apple"));assertEquals(2,calls.get());
    }
    @Test void sharedProviderQuotaRejectsBeforeHttpAndPropagatesRetryAfter() {
        doThrow(new TooManyRequestsException(7)).when(budgets).check(anyList());
        var rejected=assertThrows(TooManyRequestsException.class,()->client.translateAll("en",List.of("apple")));
        assertEquals(7,rejected.retryAfterSeconds());assertEquals(0,calls.get());
    }
    @Test void parallelIdenticalLookupsShareOneProviderRequest()throws Exception {
        delay=150;var pool=Executors.newFixedThreadPool(8);var go=new CountDownLatch(1);
        try{var futures=new ArrayList<Future<String>>();for(int n=0;n<8;n++)futures.add(pool.submit(()->{go.await();return client.translateAll("en",List.of("apple")).get("apple");}));
            go.countDown();for(var future:futures)assertEquals("alma",future.get(3,TimeUnit.SECONDS));assertEquals(1,calls.get());}
        finally{pool.shutdownNow();}
    }
    @Test void batchTranslatesConcurrentlyButNeverExceedsFourProviderCalls() {
        delay=40;var words=List.of("one","two","three","four","five","six","seven","eight");
        assertEquals(8,client.translateAll("en",words).size());assertEquals(8,calls.get());assertTrue(maximum.get()<=4);assertTrue(maximum.get()>1);
    }
    @Test void slowProviderHasABoundedDeadlineAndCanBeRetried()throws Exception {
        delay=1000;long started=System.nanoTime();assertThrows(TranslationUnavailableException.class,()->client.translateAll("en",List.of("apple")));
        assertTrue(Duration.ofNanos(System.nanoTime()-started).toMillis()<1800);Thread.sleep(100);delay=0;
        assertEquals("alma",client.translateAll("en",List.of("apple")).get("apple"));
    }
    @Test void oversizedMalformedAndWarningResponsesAreNeverCachedAsMeanings() {
        for(String invalid:List.of("x".repeat(70000),"{bad","{\"responseStatus\":429,\"responseData\":{\"translatedText\":\"quota\"}}",
                "{\"responseStatus\":200,\"responseData\":{\"translatedText\":\"MYMEMORY WARNING quota\"}}",
                "{\"responseStatus\":200,\"responseData\":{\"translatedText\":\""+"x".repeat(201)+"\"}}")) {
            body.set(invalid);assertThrows(TranslationUnavailableException.class,()->client.translateAll("en",List.of(UUID.randomUUID().toString())));
        }
    }
    @Test void providerHttpErrorsDoNotBecomeMeaningsAndPartialBatchesFail() {
        status.set(503);assertThrows(TranslationUnavailableException.class,()->client.translateAll("en",List.of("one","two")));
        status.set(200);assertEquals("alma",client.translateAll("en",List.of("one")).get("one"));
    }
    @Test void immediateRetryAfterFailedFlightStartsFreshProviderWork() {
        for(int n=0;n<20;n++) {
            String word="retry"+n;status.set(503);
            assertThrows(TranslationUnavailableException.class,()->client.translateAll("en",List.of(word)));
            status.set(200);
            assertEquals("alma",client.translateAll("en",List.of(word)).get(word));
        }
        assertEquals(40,calls.get());
    }
    @Test void wordAndLanguageParametersAreEncodedAsValues() {
        client.translateAll("en",List.of("c++ & apple?"));
        var decoded=new HashMap<String,String>();for(String pair:query.get().split("&")) {
            String[] parts=pair.split("=",2);decoded.put(parts[0],URLDecoder.decode(parts[1],StandardCharsets.UTF_8));
        }
        assertEquals("c++ & apple?",decoded.get("q"));assertEquals("en|hu",decoded.get("langpair"));assertEquals(2,decoded.size());
    }
    @Test void excessConcurrentRequestsAreRejectedWithoutUnboundedProviderWork()throws Exception {
        delay=300;var pool=Executors.newFixedThreadPool(24);var go=new CountDownLatch(1);
        try {
            var futures=new ArrayList<Future<Boolean>>();for(int n=0;n<24;n++){
                String word="word"+n;futures.add(pool.submit(()->{go.await();try{client.translateAll("en",List.of(word));return true;}catch(TranslationUnavailableException busy){return false;}}));
            }
            go.countDown();int failed=0;for(var future:futures)if(!future.get(5,TimeUnit.SECONDS))failed++;
            assertTrue(failed>0);assertTrue(maximum.get()<=4);
        }finally{pool.shutdownNow();}
    }
    @Test void cacheExpiryAndProviderQuotaAreBounded() {
        client.close();var instant=new AtomicReference<>(Instant.parse("2026-10-28T10:00:00Z"));
        Clock mutable=new Clock(){public ZoneId getZone(){return ZoneOffset.UTC;}public Clock withZone(ZoneId zone){return this;}public Instant instant(){return instant.get();}};
        client=new TranslationClient(mutable,"http://127.0.0.1:"+server.getAddress().getPort()+"/get",2000,budgets);
        for(int n=0;n<120;n++)client.translateAll("en",List.of("word"+n));
        assertThrows(TooManyRequestsException.class,()->client.translateAll("en",List.of("over-quota")));
        client.translateAll("en",List.of("word0"));assertEquals(120,calls.get());
        instant.set(instant.get().plusSeconds(601));client.translateAll("en",List.of("word0"));assertEquals(121,calls.get());
    }
}
