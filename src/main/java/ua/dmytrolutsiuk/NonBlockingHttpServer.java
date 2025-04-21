package ua.dmytrolutsiuk;

import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

@Slf4j
public class NonBlockingHttpServer {

    private static final int PORT = 8080;
    private static final String ROOT_DIR = "/Users/dmytrolutsiuk/Desktop/Универ/Паралельні обчислення" +
            "/parallel-computing-lab5/src/main/resources/pages";
    private static final String HEADER_DELIMITER = "\r\n\r\n";
    private static final String HTTP_GET_METHOD = "GET";

    private static final Map<String, String> pageCache = new HashMap<>();

    private static final String NOT_FOUND_BODY = "<h1>404 Not Found</h1>";
    private static final String NOT_FOUND_RESPONSE = buildHttpResponse(404, NOT_FOUND_BODY);

    public static void main(String[] args) {
        preloadStaticFiles();
        try {
            Selector selector = Selector.open();
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();
            serverSocketChannel.bind(new InetSocketAddress(PORT));
            serverSocketChannel.configureBlocking(false);
            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
            log.info("Non-blocking server started on port {}", PORT);

            while (true) {
                selector.select();
                Set<SelectionKey> selectedKeys = selector.selectedKeys();
                Iterator<SelectionKey> keyIterator = selectedKeys.iterator();
                while (keyIterator.hasNext()) {
                    SelectionKey key = keyIterator.next();
                    keyIterator.remove();

                    if (key.isAcceptable()) {
                        handleAccept(serverSocketChannel, selector);
                    } else if (key.isReadable()) {
                        handleRead(key);
                    }
                }
            }
        } catch (IOException e) {
            log.error("Error starting server", e);
        }
    }

    private static void preloadStaticFiles() {
        Path rootPath = Paths.get(ROOT_DIR);
        try (var paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile).forEach(path -> {
                try {
                    String relativePath = "/" + rootPath.relativize(path).toString().replace("\\", "/");
                    String content = Files.readString(path);
                    pageCache.put(relativePath, content);
                    log.info("Cached: {}", relativePath);
                } catch (IOException e) {
                    log.error("Failed to read file: {}", path, e);
                }
            });
        } catch (IOException e) {
            log.error("Error during file preloading", e);
        }
    }

    private static void handleAccept(ServerSocketChannel serverChannel, Selector selector) throws IOException {
        var clientChannel = serverChannel.accept();
        clientChannel.configureBlocking(false);
        clientChannel.register(selector, SelectionKey.OP_READ, ByteBuffer.allocate(2048));
    }

    private static void handleRead(SelectionKey key) {
        var clientChannel = (SocketChannel) key.channel();
        var buffer = (ByteBuffer) key.attachment();
        try {
            int bytesRead = clientChannel.read(buffer);
            if (bytesRead == -1) {
                clientChannel.close();
                return;
            }
            String request = new String(buffer.array(), 0, buffer.position(), StandardCharsets.UTF_8);
            if (!request.contains(HEADER_DELIMITER)) return;

            String[] lines = request.split("\r\n");
            String[] requestLine = lines[0].split(" ");
            String method = requestLine[0];
            String path = requestLine[1];

            if (!method.equals(HTTP_GET_METHOD)) {
                clientChannel.close();
                return;
            }

            if (path.equals("/")) path = "/index.html";

            String body = pageCache.get(path);
            String response = (body != null)
                    ? buildHttpResponse(200, body)
                    : NOT_FOUND_RESPONSE;

            byte[] responseBytes = response.getBytes(StandardCharsets.UTF_8);
            clientChannel.write(ByteBuffer.wrap(responseBytes));
            clientChannel.close();
        } catch (IOException e) {
            log.error("Error handling client request", e);
            try {
                clientChannel.close();
            } catch (IOException ex) {
                log.error("Error closing client channel", ex);
            }
        }
    }

    private static String buildHttpResponse(int statusCode, String body) {
        String statusText = switch (statusCode) {
            case 200 -> "OK";
            case 404 -> "Not Found";
            default -> "Internal Server Error";
        };
        return "HTTP/1.1 " + statusCode + " " + statusText + "\r\n" +
                "Content-Type: text/html\r\n" +
                "Content-Length: " + body.getBytes(StandardCharsets.UTF_8).length + "\r\n" +
                "Connection: close\r\n" +
                "\r\n" +
                body;
    }
}
