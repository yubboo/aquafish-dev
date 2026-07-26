package com.aquafish.core.redis;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.net.ServerSocket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import org.junit.jupiter.api.Test;

/**
 * Redis RESP 检测器测试，使用本机临时端口模拟 PING/PONG，不依赖外部 Redis。
 */
class RedisConnectionTesterTest {

    @Test
    void disabledRedisIsSkippedWithoutNetworkAccess() {
        RedisConnectionTestResult result = new RedisConnectionTester()
            .test(RedisSettings.disabled())
            .block(Duration.ofSeconds(2));

        assertNotNull(result);
        assertTrue(result.connected());
        assertTrue(result.skipped());
    }

    @Test
    void pingSucceedsAgainstRespServer() throws Exception {
        try (ServerSocket server = new ServerSocket(0)) {
            Thread responder = Thread.ofVirtual().start(() -> {
                try (var socket = server.accept()) {
                    ByteArrayOutputStream received = new ByteArrayOutputStream();
                    while (!received.toString(StandardCharsets.UTF_8).contains("PING")) {
                        int value = socket.getInputStream().read();
                        if (value < 0) return;
                        received.write(value);
                    }
                    socket.getOutputStream().write("+PONG\r\n".getBytes(StandardCharsets.UTF_8));
                    socket.getOutputStream().flush();
                } catch (Exception error) {
                    throw new IllegalStateException(error);
                }
            });

            RedisSettings settings = new RedisSettings(
                true, "127.0.0.1", server.getLocalPort(), 0, "", "", false
            );
            RedisConnectionTestResult result = new RedisConnectionTester()
                .test(settings)
                .block(Duration.ofSeconds(5));

            assertNotNull(result);
            assertTrue(result.connected(), result.message());
            responder.join(Duration.ofSeconds(2));
        }
    }
}
