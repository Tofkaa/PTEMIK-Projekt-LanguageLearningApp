package com.languageapp.backend.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.languageapp.backend.exception.TranslationUnavailableException;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.*;
import java.nio.ByteBuffer;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.Flow;

/** Bounded per-instance outbound work; only public provider translations are cached, never user data. */
@Component
public class TranslationClient {
    private static final int PARALLELISM=4, CACHE_LIMIT=512, MAX_BODY=64*1024;
    private final Clock clock;
    private final URI endpoint;
    private final int timeoutMillis;
    private final HttpClient http;
    private final ObjectMapper json=new ObjectMapper(com.fasterxml.jackson.core.JsonFactory.builder()
            .streamReadConstraints(com.fasterxml.jackson.core.StreamReadConstraints.builder().maxNestingDepth(32).build()).build());
    private final ThreadPoolExecutor workers=new ThreadPoolExecutor(PARALLELISM,PARALLELISM,30,TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(4),task->{Thread thread=new Thread(task,"dictionary-translation");thread.setDaemon(true);return thread;},new ThreadPoolExecutor.AbortPolicy());
    private final Map<Key,CompletableFuture<String>> flights=new HashMap<>();
    private final LinkedHashMap<Key,Cached> cache=new LinkedHashMap<>(16,0.75f,true);
    private final Object gate=new Object();
    private Instant quotaStart;
    private int requests;
    private record Key(String language,String word) { }
    private record Cached(String translation,Instant expiresAt) { }

    public TranslationClient(Clock clock,@Value("${app.translation.url:https://api.mymemory.translated.net/get}") String endpoint,
            @Value("${app.translation.timeout-ms:4000}") int timeoutMillis) {
        this.clock=clock;this.endpoint=URI.create(endpoint);
        if(timeoutMillis<100 || timeoutMillis>10000)throw new IllegalArgumentException("Invalid dictionary timeout");
        this.timeoutMillis=timeoutMillis;
        this.http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).followRedirects(HttpClient.Redirect.NEVER).build();
    }
    /** All-or-nothing result, bounded wait and a sliding window of at most four pending words per request. */
    public Map<String,String> translateAll(String language,List<String> words) {
        long deadline=System.nanoTime()+TimeUnit.SECONDS.toNanos(10);
        Iterator<String> next=new LinkedHashSet<>(words).iterator();
        Map<String,CompletableFuture<String>> pending=new LinkedHashMap<>();
        Map<String,String> result=new LinkedHashMap<>();
        while(next.hasNext() || !pending.isEmpty()) {
            while(next.hasNext() && pending.size()<PARALLELISM){String word=next.next();pending.put(word,start(new Key(language,word)));}
            var first=pending.entrySet().iterator().next();
            try {
                long remaining=deadline-System.nanoTime();if(remaining<=0)throw new TimeoutException();
                result.put(first.getKey(),first.getValue().get(remaining,TimeUnit.NANOSECONDS));pending.remove(first.getKey());
            } catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new TranslationUnavailableException();}
            catch(ExecutionException | TimeoutException rejected){throw new TranslationUnavailableException();}
        }
        return Map.copyOf(result);
    }
    private CompletableFuture<String> start(Key key) {
        synchronized(gate) {
            Instant now=clock.instant();Cached hit=cache.get(key);
            if(hit!=null && now.isBefore(hit.expiresAt()))return CompletableFuture.completedFuture(hit.translation());
            cache.remove(key);
            var existing=flights.get(key);if(existing!=null)return existing;
            if(quotaStart==null || !now.isBefore(quotaStart.plusSeconds(60))){quotaStart=now;requests=0;}
            if(requests>=120)throw new TranslationUnavailableException();
            var promise=new CompletableFuture<String>();flights.put(key,promise);
            try {
                workers.execute(()->{
                    try {
                        String translation=fetch(key);
                        synchronized(gate) {
                            cache.put(key,new Cached(translation,clock.instant().plusSeconds(600)));
                            while(cache.size()>CACHE_LIMIT)cache.remove(cache.keySet().iterator().next());
                        }
                        promise.complete(translation);
                    }catch(RuntimeException rejected){promise.completeExceptionally(new TranslationUnavailableException());}
                    finally{synchronized(gate){flights.remove(key,promise);}}
                });requests++;
            }catch(RejectedExecutionException busy){flights.remove(key,promise);throw new TranslationUnavailableException();}
            return promise;
        }
    }
    private String fetch(Key key) {
        URI uri=UriComponentsBuilder.fromUri(endpoint).queryParam("q","{word}").queryParam("langpair","{pair}").encode()
                .buildAndExpand(Map.of("word",key.word(),"pair",key.language()+"|hu")).toUri();
        var request=HttpRequest.newBuilder(uri).timeout(Duration.ofMillis(timeoutMillis)).GET().build();
        var response=http.sendAsync(request,info->new LimitedBody());
        try {
            var received=response.get(timeoutMillis,TimeUnit.MILLISECONDS);
            if(received.statusCode()!=200)throw new TranslationUnavailableException();
            var root=json.readTree(received.body());
            var value=root.path("responseData").path("translatedText");
            if(root.path("responseStatus").asInt()!=200 || !value.isTextual())throw new TranslationUnavailableException();
            String text=value.asText().trim();
            if(text.isBlank() || text.length()>200 || text.toUpperCase(Locale.ROOT).contains("MYMEMORY WARNING"))throw new TranslationUnavailableException();
            return text;
        }catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new TranslationUnavailableException();}
        catch(Exception rejected){throw new TranslationUnavailableException();}
        finally{if(!response.isDone())response.cancel(true);}
    }
    /** Cancel oversized responses before buffering them; no provider body/URL is logged. */
    private static final class LimitedBody implements HttpResponse.BodySubscriber<byte[]> {
        private final CompletableFuture<byte[]> result=new CompletableFuture<>();
        private final ByteArrayOutputStream bytes=new ByteArrayOutputStream();
        private Flow.Subscription subscription;
        @Override public CompletionStage<byte[]> getBody(){return result;}
        @Override public void onSubscribe(Flow.Subscription subscription){this.subscription=subscription;subscription.request(1);}
        @Override public void onNext(List<ByteBuffer> buffers){
            for(var buffer:buffers){if(buffer.remaining()>MAX_BODY-bytes.size()){subscription.cancel();result.completeExceptionally(new TranslationUnavailableException());return;}
                byte[] chunk=new byte[buffer.remaining()];buffer.get(chunk);bytes.writeBytes(chunk);}
            subscription.request(1);
        }
        @Override public void onError(Throwable error){result.completeExceptionally(new TranslationUnavailableException());}
        @Override public void onComplete(){result.complete(bytes.toByteArray());}
    }
    @PreDestroy public void close(){workers.shutdownNow();http.close();}
}
