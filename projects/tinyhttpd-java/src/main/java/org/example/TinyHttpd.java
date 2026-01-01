package org.example;

import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

public class TinyHttpd {

    private static final String SERVER_STRING = "Server: jdbhttpd/0.1.0\r\n";

    private static void acceptRequest(Socket socket) throws IOException, URISyntaxException, InterruptedException {
        try (socket;
             BufferedWriter bufferedWriter = new BufferedWriter(
                     new OutputStreamWriter(socket.getOutputStream(), StandardCharsets.UTF_8), 1024);
             BufferedReader bufferedReader = new BufferedReader(
                     new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8), 1024)) {

            // 解析第一行
            String line = getLine(bufferedReader);
            if (line == null) {
                unimplemented(bufferedWriter);
                return;
            }

            String[] split = line.split("\\s+");
            // method
            String method = split[0];
            if (!"GET".equalsIgnoreCase(method) && !"POST".equalsIgnoreCase(method)) {
                unimplemented(bufferedWriter);
                return;
            }

            // cgi
            boolean cgi = "POST".equalsIgnoreCase(method);

            // url
            String url = split.length > 1 ? split[1] : "";
            int index = url.indexOf("?");
            String path = url.substring(0, index == -1 ? url.length() : index);
            String query = index == -1 ? "" : url.substring(index + 1);

            if ("GET".equalsIgnoreCase(method) && !query.isEmpty()) {
                cgi = true;
            }

            String finalPath = "htdocs" + path;
            if (finalPath.endsWith("/")) {
                finalPath = finalPath + "index.html";
            }

            URL resource = ClassLoader.getSystemResource(finalPath);
            if (resource == null) {
                // todo
                while (line != null && !line.isEmpty()) {
                    line = getLine(bufferedReader);
                }
                notFound(bufferedWriter);
            } else {
                if (isDir(resource)) {
                    finalPath = finalPath + "/index.html";
                }

                if (executable(finalPath)) {
                    cgi = true;
                }

                if (!cgi) {
                    serveFile(bufferedReader, bufferedWriter, finalPath);
                } else {
                    executeCgi(bufferedReader, bufferedWriter, finalPath, method, query);
                }
            }
        }
    }

    private static boolean executable(String path) throws URISyntaxException {
        URL resource = ClassLoader.getSystemResource(path);
        if (resource == null) {
            return false;
        }

        File file = new File(resource.toURI());
        return file.isFile() && file.canExecute();
    }

    private static boolean isDir(URL url) throws URISyntaxException {
        File file = new File(url.toURI());
        return file.isDirectory();
    }

    private static void badRequest(BufferedWriter bufferedWriter) throws IOException {
        String str = "HTTP/1.0 400 BAD REQUEST\r\n" +
                "Content-type: text/html\r\n" +
                "\r\n" +
                "<P>Your browser sent a bad request, " +
                "such as a POST without a Content-Length.\r\n";
        bufferedWriter.write(str);
        bufferedWriter.flush();
    }

    private static void cat(BufferedWriter bufferedWriter, URL resource) throws IOException {
        try (BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(resource.openStream()))) {
            char[] buffer = new char[1024];
            int read = bufferedReader.read(buffer);
            while (read != -1) {
                bufferedWriter.write(buffer, 0, read);
                read = bufferedReader.read(buffer);
            }
        }
    }

    private static void cannotExecute(BufferedWriter bufferedWriter) throws IOException {
        String str = "HTTP/1.0 500 Internal Server Error\r\n" +
                "Content-type: text/html\r\n" +
                "\r\n" +
                "<P>Error prohibited CGI execution.\r\n";
        bufferedWriter.write(str);
        bufferedWriter.flush();
    }

    private static void executeCgi(BufferedReader bufferedReader, BufferedWriter bufferedWriter, String path, String method, String query) throws IOException, InterruptedException, URISyntaxException {
        URL resource = ClassLoader.getSystemResource(path);
        if (resource == null) {
            notFound(bufferedWriter);
            return;
        }

        int contentLength = -1;

        if ("GET".equalsIgnoreCase(method)) {
            String line = "A";
            // todo
            while (line != null && !line.isEmpty()) {
                line = getLine(bufferedReader);
            }
        } else if ("POST".equalsIgnoreCase(method)) {
            String line = getLine(bufferedReader);
            while (line != null && !line.isEmpty()) {
                if (line.startsWith("Content-Length:")) {
                    contentLength = Integer.parseInt(line.substring("Content-Length: ".length()));
                }
                line = getLine(bufferedReader);
            }

            if (contentLength == -1) {
                badRequest(bufferedWriter);
                return;
            }
        } else {
            unimplemented(bufferedWriter);
            return;
        }

        File file = new File(resource.toURI());

        Map<String, String> env = new HashMap<>();
        env.put("REQUEST_METHOD", method);
        env.put("SCRIPT_FILENAME", file.getAbsolutePath());
        env.put("SERVER_PROTOCOL", "HTTP/1.1");

        if ("GET".equalsIgnoreCase(method)) {
            env.put("QUERY_STRING", query);
        }

        if ("POST".equalsIgnoreCase(method)) {
            env.put("CONTENT_LENGTH", String.valueOf(contentLength));
        }

        ProcessBuilder pb = new ProcessBuilder(file.getAbsolutePath());
        Map<String, String> environment = pb.environment();
        environment.putAll(env);
        pb.directory(file.getParentFile());

        Process process = pb.start();

        if ("POST".equalsIgnoreCase(method)) {
            try(BufferedWriter cgiWriter = new BufferedWriter(new OutputStreamWriter(process.getOutputStream()))) {
                char[] buffer = new char[1024];
                int read = -1;
                int totalRead = 0;
                while (totalRead < contentLength && (read = bufferedReader.read(buffer, 0, Math.min(buffer.length, contentLength - totalRead))) != -1) {
                    cgiWriter.write(buffer, 0, read);
                    totalRead += read;
                }
            }
        }

        try (BufferedReader cgiReader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            boolean headersSent = false;

            while ((line = cgiReader.readLine()) != null) {
                if (!headersSent) {
                    // 发送HTTP响应头
                    bufferedWriter.write("HTTP/1.1 200 OK\r\n");
                    bufferedWriter.write("Server: Java CGI Handler\r\n");
                    headersSent = true;
                }
                bufferedWriter.write(line + "\r\n");
            }
        }

        boolean completed = process.waitFor(60, TimeUnit.SECONDS);
        if (!completed) {
            process.destroyForcibly();
            cannotExecute(bufferedWriter);
        }

        int exitCode = process.exitValue();
        if (exitCode != 0) {
            System.err.println("CGI script exited with code: " + exitCode);
        }

        bufferedWriter.flush();

    }

    private static String getLine(BufferedReader bufferedReader) throws IOException {
        return bufferedReader.readLine();
    }

    private static void headers(BufferedWriter bufferedWriter, String filename) throws IOException {
        String str = "HTTP/1.0 200 OK\r\n" +
                SERVER_STRING +
                "Content-Type: text/html\r\n" +
                "\r\n";

        bufferedWriter.write(str);
    }

    private static void notFound(BufferedWriter bufferedWriter) throws IOException {
        String str = "HTTP/1.0 404 NOT FOUND\r\n" +
                SERVER_STRING +
                "Content-Type: text/html\r\n" +
                "\r\n" +
                "<HTML><TITLE>Not Found</TITLE>\r\n" +
                "<BODY><P>The server could not fulfill\r\n" +
                "your request because the resource specified\r\n" +
                "is unavailable or nonexistent.\r\n" +
                "</BODY></HTML>\r\n";
        bufferedWriter.write(str);
        bufferedWriter.flush();
    }

    private static void serveFile(BufferedReader bufferedReader, BufferedWriter bufferedWriter, String path) throws IOException {
        String line = "A";
        // todo
        while (line != null && !line.isEmpty()) {
            line = getLine(bufferedReader);
        }

        URL resource = ClassLoader.getSystemResource(path);
        if (resource == null) {
            notFound(bufferedWriter);
        } else {
            headers(bufferedWriter, path);
            cat(bufferedWriter, resource);
            bufferedWriter.flush();
        }
    }

    private static ServerSocket startup(int port) throws IOException {
        return new ServerSocket(port);
    }

    private static void unimplemented(BufferedWriter bufferedWriter) throws IOException {
        String str = "HTTP/1.0 501 Method Not Implemented\\r\\n" +
                SERVER_STRING +
                "Content-Type: text/html\r\n" +
                "\r\n" +
                "<HTML><HEAD><TITLE>Method Not Implemented\r\n" +
                "</TITLE></HEAD>\r\n" +
                "<BODY><P>HTTP request method not supported.\r\n" +
                "</BODY></HTML>\r\n";
        bufferedWriter.write(str);
        bufferedWriter.flush();
    }

    private static ThreadPoolExecutor newThreadPoolExecutor() {
        int corePoolSize = Runtime.getRuntime().availableProcessors();
        int maximumPoolSize = Runtime.getRuntime().availableProcessors() * 2;
        return new ThreadPoolExecutor(
                corePoolSize,
                maximumPoolSize,
                60L, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(100),
                newThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy());

    }

    private static ThreadFactory newThreadFactory() {
        return new ThreadFactory() {
            private final AtomicInteger threadNumber = new AtomicInteger(1);

            @Override
            public Thread newThread(Runnable r) {
                return new Thread(r, String.format("httpd-%s", threadNumber.getAndIncrement()));
            }
        };
    }

    public static void main(String[] args) throws IOException {
        int port = 4000;

        try (ThreadPoolExecutor threadPoolExecutor = newThreadPoolExecutor();
             ServerSocket serverSocket = startup(port)) {

            log("httpd running on port " + port);

            while (true) {
                Socket socket = serverSocket.accept();
                if (socket == null) {
                    throw new SocketException("Socket accept failed");
                }

                threadPoolExecutor.execute(() -> {
                    try {
                        acceptRequest(socket);
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                });
            }
        }

    }

    private static void log(String msg) {
        System.out.println(msg);
    }

}
