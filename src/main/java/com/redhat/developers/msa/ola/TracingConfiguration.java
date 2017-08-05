package com.redhat.developers.msa.ola;

import java.util.Collection;
import java.util.Collections;
import java.util.regex.Pattern;

import feign.Request;
import feign.RequestInterceptor;
import feign.RequestTemplate;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import com.uber.jaeger.metrics.Metrics;
import com.uber.jaeger.metrics.NullStatsReporter;
import com.uber.jaeger.metrics.StatsFactoryImpl;
import com.uber.jaeger.reporters.RemoteReporter;
import com.uber.jaeger.samplers.ProbabilisticSampler;
import com.uber.jaeger.senders.Sender;
import com.uber.jaeger.senders.UdpSender;

import feign.Logger;
import feign.hystrix.HystrixFeign;
import feign.jackson.JacksonDecoder;
import io.opentracing.NoopTracerFactory;
import io.opentracing.Tracer;
import io.opentracing.contrib.spring.web.autoconfig.WebTracingConfiguration;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;

/**
 * @author Pavol Loffay
 */
@Configuration
@Slf4j
public class TracingConfiguration {


    //The Tracing Headers that needs to be propagated as part of each request
    private static final String[] TRACING_HEADERS = {"x-request-id",
            "x-b3-traceid", "x-b3-spanid", "x-b3-parentspanid",
            "x-b3-sampled", "x-b3-flags", "x-ot-span-context"
    };

//START BEFORE - ISTIO
//    private static final String SERVICE_NAME = "ola";
//    @Bean
//    public Tracer tracer() {
//        String jaegerURL = System.getenv("JAEGER_SERVER_HOSTNAME");
//        if (jaegerURL != null) {
//            log.info("Using Jaeger tracer");
//            return jaegerTracer(jaegerURL);
//        }
//
//        log.info("Using Noop tracer");
//        return NoopTracerFactory.create();
//    }
//
//
//    private Tracer jaegerTracer(String url) {
//        Sender sender = new UdpSender(url, 0, 0);
//        return new com.uber.jaeger.Tracer.Builder(SERVICE_NAME,
//                new RemoteReporter(sender, 100, 50,
//                        new Metrics(new StatsFactoryImpl(new NullStatsReporter()))),
//                new ProbabilisticSampler(1.0))
//                .build();
//    }
//
//
//    @Bean
//    public WebTracingConfiguration webTracingConfiguration() {
//        return WebTracingConfiguration.builder()
//                .withSkipPattern(Pattern.compile("/api/health"))
//                .build();
//    }
//END BEFORE - ISTIO

    /**
     * This is were the "magic" happens: it creates a Feign, which is a proxy interface for remote calling a
     * REST endpoint with Hystrix fallback support.
     */
    @Bean
    public HolaService holaService() {
        return HystrixFeign.builder()
                .logger(new Logger.ErrorLogger()).logLevel(Logger.Level.BASIC)
                .decoder(new JacksonDecoder())
                .requestInterceptor(this::applyTracingHeaders) // extra tracing headers required to be propagated
                .target(HolaService.class, "http://hola:8080/",
                        () -> Collections.singletonList("Hola response (fallback)"));
    }

    @Bean
    public AlohaService alohaService() {

        return HystrixFeign.builder()
                .logger(new Logger.ErrorLogger()).logLevel(Logger.Level.BASIC)
                .decoder(new JacksonDecoder())
                .requestInterceptor(this::applyTracingHeaders) // extra tracing headers required to be propagated
                .target(AlohaService.class, "http://istio-ingress/",
                        () -> Collections.singletonList("Aloha response (fallback)"));
    }

    protected void applyTracingHeaders(RequestTemplate requestTemplate) {
        for (String headerName : TRACING_HEADERS) {
            Request request = requestTemplate.request();
            if (!request.headers().containsKey(headerName)) {
                Collection<String> headerValues = request.headers().get(headerName);
                if (headerValues != null) {
                    log.info("Adding Tracing Header {} with value {}",
                            headerName, headerValues);
                    requestTemplate.header(headerName, headerValues);
                }
            }
        }
    }
}
