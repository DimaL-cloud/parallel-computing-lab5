package ua.dmytrolutsiuk;

import lombok.extern.slf4j.Slf4j;

import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;

@Slf4j
public class BlockingHttpServer {

    private static final int PORT = 8080;
    private static final String ROOT_DIR = "/Users/dmytrolutsiuk/Desktop/Универ/Паралельні обчислення" +
            "/parallel-computing-lab5/src/main/resources/pages";
    private static final String HTTP_GET_METHOD = "GET";
    private static final Map<String, String> pageCache = new ConcurrentHashMap<>();

    public static void main(String[] args) {
        preloadStaticFiles();
        var threadPool = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

        try (var serverSocket = new ServerSocket(PORT)) {
            log.info("Blocking server started on port {}", PORT);
            while (true) {
                var clientSocket = serverSocket.accept();
                threadPool.submit(() -> handleClientRequest(clientSocket));
            }
        } catch (IOException e) {
            log.error("Error starting server", e);
        }
    }

    private static void preloadStaticFiles() {
        var rootPath = Paths.get(ROOT_DIR);
        try (var paths = Files.walk(rootPath)) {
            paths.filter(Files::isRegularFile)
                    .forEach(path -> {
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

    private static void handleClientRequest(Socket clientSocket) {
        try (clientSocket;
             var inputStream = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             var outputStream = clientSocket.getOutputStream();
             var writer = new PrintWriter(outputStream, true)
        ) {
            String requestLine = inputStream.readLine();
            if (requestLine == null || !requestLine.startsWith(HTTP_GET_METHOD)) {
                return;
            }
            String[] tokens = requestLine.split(" ");
            String path = tokens[1];
            if (path.equals("/")) {
                path = "/index.html";
            }
            String pageContent = pageCache.get(path);
            if (pageContent != null) {
                writer.print("HTTP/1.1 200 OK\r\n");
                writer.print("Content-Type: text/html\r\n");
                writer.print("Content-Length: " + pageContent.getBytes().length + "\r\n");
                writer.print("Connection: close\r\n");
                writer.print("\r\n");
                writer.print(pageContent);
            } else {
                String notFoundHtml = "<h1>404 Not Found</h1>";
                writer.print("HTTP/1.1 404 Not Found\r\n");
                writer.print("Content-Type: text/html\r\n");
                writer.print("Content-Length: " + notFoundHtml.getBytes().length + "\r\n");
                writer.print("Connection: close\r\n");
                writer.print("\r\n");
                writer.print(notFoundHtml);
            }
            writer.flush();
        } catch (IOException e) {
            log.error("Error handling request", e);
        }
    }
}
