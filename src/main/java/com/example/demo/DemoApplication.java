package com.example.demo;

import io.netty.channel.ChannelOption;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.ipc.netty.resources.LoopResources;
import reactor.ipc.netty.resources.PoolResources;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@SpringBootApplication
public class DemoApplication {

    public static void main(String[] args) {
        SpringApplication.run(DemoApplication.class, args);
    }

    @RestController
    public class TestController {
        private static final int TIMEOUT = 60000;
        private static final int MAX_CONNECTIONS = 500;

        private List<WebClient> webClients = Arrays.asList(
                WebClient.builder().baseUrl("http://10.153.202.11:8080").clientConnector(buildConnector(1)).build(),
                WebClient.builder().baseUrl("http://10.153.202.12:8080").clientConnector(buildConnector(2)).build(),
                WebClient.builder().baseUrl("http://10.153.202.13:8080").clientConnector(buildConnector(3)).build()
        );

        private WebClient webClient = WebClient.builder().baseUrl("http://10.153.202.11:8080").clientConnector(buildConnector(1)).build();

        private ReactorClientHttpConnector buildConnector(int poolNumber) {
            return new ReactorClientHttpConnector(options -> options
                    .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, TIMEOUT)
                    .option(ChannelOption.TCP_NODELAY, true)
                    .preferNative(true)
                    .loopResources(LoopResources.create("loop" + poolNumber, 1, 2, false))
                    .poolResources(PoolResources.fixed("connector" + poolNumber, MAX_CONNECTIONS))
                    .afterNettyContextInit(nettyContext -> nettyContext.addHandlerLast(new ReadTimeoutHandler(1000, TimeUnit.MILLISECONDS)))
            );
        }

        @RequestMapping(method = RequestMethod.GET, value = "/direct")
        public Mono<Void> direct(@RequestParam Integer sleep) {
            ThreadLocalRandom random = ThreadLocalRandom.current();
            int index = random.nextInt(webClients.size());
            return request(webClients.get(index), sleep);
        }

        @RequestMapping(method = RequestMethod.GET, value = "/one")
        public Mono<Void> one(@RequestParam Integer sleep) {
            return request(webClient, sleep);
        }

        @RequestMapping(method = RequestMethod.GET, value = "/delay")
        public Mono<String> delay(@RequestParam Integer sleep) {
            return Mono.just("sleep " + sleep.toString() + "ms").delayElement(Duration.ofMillis(sleep));
        }

        private Mono<Void> request(WebClient webClient, Integer sleep) {
            return webClient
                    .get()
                    .uri("serviceA/sleepNms.action?n=" + sleep)
                    .exchange()
                    .flatMap(r -> r.bodyToMono(Void.class));
        }
    }
}
