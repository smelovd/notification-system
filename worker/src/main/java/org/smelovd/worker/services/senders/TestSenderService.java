package org.smelovd.worker.services.senders;

import lombok.extern.slf4j.Slf4j;
import org.smelovd.worker.entities.NotificationStatus;
import org.smelovd.worker.exceptions.ServiceSenderException;
import org.smelovd.worker.services.senders.interfaces.NotificationService;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import reactor.core.publisher.Mono;
import reactor.util.retry.Retry;

import java.time.Duration;

import static org.smelovd.worker.entities.NotificationStatus.*;

@Slf4j
@Service
public class TestSenderService implements NotificationService {

    private static final String URL = "http://172.17.0.1:80/test";
    private final WebClient webClient = WebClient.create(URL);

    public Mono<NotificationStatus> send(String serviceUserId, Mono<String> message) {
        return webClient.post()
                .exchangeToMono(response -> Mono.just(response.statusCode()))
                .<NotificationStatus>handle((httpStatusCode, synchronousSink) -> {
                    if (httpStatusCode.is2xxSuccessful()) {
                        synchronousSink.next(DONE);
                    } else if (httpStatusCode.is4xxClientError()) {
                        synchronousSink.next(CLIENT_ERROR);
                    } else if (httpStatusCode.is5xxServerError()) {
                        synchronousSink.error(new ServiceSenderException("Service error", httpStatusCode));
                    } else {
                        log.error("unknown exception");
                    }
                })
                .retryWhen(Retry.fixedDelay(5, Duration.ofMillis(5000))
                        .filter(throwable -> throwable instanceof ServiceSenderException))
                .onErrorResume(error -> Mono.just(SERVER_ERROR));
    }
}
