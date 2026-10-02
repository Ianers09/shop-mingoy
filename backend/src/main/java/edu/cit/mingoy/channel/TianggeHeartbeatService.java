package edu.cit.mingoy.channel;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
class TianggeHeartbeatService {

    private final TianggeClient tianggeClient;
    private final InstanceIdentity instanceIdentity;
    private final long heartbeatIntervalMs;

    private final Instant startedAt;

    TianggeHeartbeatService(
            TianggeClient tianggeClient,
            InstanceIdentity instanceIdentity,
            @Value("${shop.tiangge.heartbeat-interval-ms}") long heartbeatIntervalMs
    ) {
        this.tianggeClient = tianggeClient;
        this.instanceIdentity = instanceIdentity;
        this.heartbeatIntervalMs = heartbeatIntervalMs;
        this.startedAt = Instant.now();
    }

    @EventListener(ApplicationReadyEvent.class)
    void sendInitialHeartbeat() {
        sendHeartbeat();
    }

    @Scheduled(fixedDelayString = "${shop.tiangge.heartbeat-interval-ms}")
    void sendScheduledHeartbeat() {
        sendHeartbeat();
    }

    private void sendHeartbeat() {
        String body = """
                {
                  "appName": "shop-mingoy",
                  "startedAt": "%s",
                  "uptimeSeconds": %d
                }
                """.formatted(
                startedAt,
                Math.max(
                        0,
                        (Instant.now().toEpochMilli()
                                - startedAt.toEpochMilli()) / 1000
                )
        );

        tianggeClient.heartbeat(body);

        System.out.println(
                "TIANGGE_HEARTBEAT_SENT instance="
                        + instanceIdentity.getInstanceId()
        );
    }
}