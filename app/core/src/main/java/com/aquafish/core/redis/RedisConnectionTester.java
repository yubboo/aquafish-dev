package com.aquafish.core.redis;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import javax.net.ssl.SSLSocketFactory;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;
import reactor.core.scheduler.Schedulers;

/**
 * 安装期 Redis PING 检测器。
 *
 * <p>这里不引入完整 Redis 客户端，只使用 RESP 协议执行 AUTH、SELECT、PING。
 * Socket 操作被明确调度到 boundedElastic，不占用 WebFlux 事件循环；该能力只在
 * 首次安装手动检测时调用，不进入业务请求热路径。</p>
 */
@Service
public class RedisConnectionTester {

    private static final int TIMEOUT_MILLIS = (int) Duration.ofSeconds(5).toMillis();

    public Mono<RedisConnectionTestResult> test(RedisSettings request) {
        RedisSettings settings = request == null
            ? RedisSettings.disabled()
            : request.normalized();

        if (!settings.enabled()) {
            return Mono.just(
                new RedisConnectionTestResult(true, true, 0, "Redis 未启用，已跳过检测。")
            );
        }

        String validation = settings.validateMessage();
        if (validation != null) {
            return Mono.just(
                new RedisConnectionTestResult(false, false, 0, validation)
            );
        }

        return Mono.fromCallable(() -> ping(settings))
            .subscribeOn(Schedulers.boundedElastic());
    }

    private RedisConnectionTestResult ping(RedisSettings settings) {
        long started = System.nanoTime();

        try (Socket socket = createSocket(settings.ssl())) {
            socket.connect(
                new InetSocketAddress(settings.host(), settings.port()),
                TIMEOUT_MILLIS
            );
            socket.setSoTimeout(TIMEOUT_MILLIS);

            InputStream input = socket.getInputStream();
            OutputStream output = socket.getOutputStream();

            if (!settings.password().isBlank()) {
                String[] auth = settings.username().isBlank()
                    ? new String[]{"AUTH", settings.password()}
                    : new String[]{"AUTH", settings.username(), settings.password()};
                writeCommand(output, auth);
                requireSuccess(readReply(input), "Redis 身份认证失败");
            }

            if (settings.database() > 0) {
                writeCommand(output, "SELECT", String.valueOf(settings.database()));
                requireSuccess(readReply(input), "Redis 数据库选择失败");
            }

            writeCommand(output, "PING");
            String reply = readReply(input);
            if (!"+PONG".equalsIgnoreCase(reply)) {
                requireSuccess(reply, "Redis PING 失败");
                throw new IllegalStateException("Redis 未返回 PONG。 ");
            }

            return new RedisConnectionTestResult(
                true,
                false,
                elapsedMillis(started),
                "Redis 连接成功。"
            );
        } catch (Exception error) {
            return new RedisConnectionTestResult(
                false,
                false,
                elapsedMillis(started),
                safeMessage(error)
            );
        }
    }

    private Socket createSocket(boolean ssl) throws IOException {
        return ssl
            ? SSLSocketFactory.getDefault().createSocket()
            : new Socket();
    }

    private void writeCommand(OutputStream output, String... parts) throws IOException {
        ByteArrayOutputStream command = new ByteArrayOutputStream();
        command.write(("*" + parts.length + "\r\n").getBytes(StandardCharsets.UTF_8));
        for (String part : parts) {
            byte[] bytes = part.getBytes(StandardCharsets.UTF_8);
            command.write(("$" + bytes.length + "\r\n").getBytes(StandardCharsets.UTF_8));
            command.write(bytes);
            command.write("\r\n".getBytes(StandardCharsets.UTF_8));
        }
        output.write(command.toByteArray());
        output.flush();
    }

    private String readReply(InputStream input) throws IOException {
        ByteArrayOutputStream reply = new ByteArrayOutputStream();
        int previous = -1;
        for (int index = 0; index < 4096; index++) {
            int current = input.read();
            if (current < 0) {
                break;
            }
            if (previous == '\r' && current == '\n') {
                byte[] bytes = reply.toByteArray();
                return new String(bytes, 0, Math.max(0, bytes.length - 1), StandardCharsets.UTF_8);
            }
            reply.write(current);
            previous = current;
        }
        throw new IOException("Redis 响应不完整。 ");
    }

    private void requireSuccess(String reply, String fallback) {
        if (reply != null && reply.startsWith("+")) {
            return;
        }
        if (reply != null && reply.startsWith("-") && reply.length() > 1) {
            throw new IllegalStateException(fallback + "：" + reply.substring(1));
        }
        throw new IllegalStateException(fallback + "。 ");
    }

    private long elapsedMillis(long started) {
        return Duration.ofNanos(System.nanoTime() - started).toMillis();
    }

    private String safeMessage(Throwable error) {
        String message = error == null ? null : error.getMessage();
        return message == null || message.isBlank()
            ? "Redis 连接失败。"
            : message;
    }
}
