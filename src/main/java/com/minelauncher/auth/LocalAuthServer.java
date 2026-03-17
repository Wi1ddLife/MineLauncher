package com.minelauncher.auth;

import com.sun.net.httpserver.HttpServer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/**
 * Tiny one-shot HTTP server that listens on localhost:59125/auth.
 * Microsoft redirects to it after login with ?code=...
 * We grab the code, show a success page, then shut down.
 */
public class LocalAuthServer {

    private static final Logger logger = LoggerFactory.getLogger(LocalAuthServer.class);
    public  static final int    PORT   = 59125;
    public  static final String PATH   = "/auth";

    private HttpServer server;
    private final CompletableFuture<String> codeFuture = new CompletableFuture<>();

    public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("localhost", PORT), 0);
        server.createContext(PATH, exchange -> {
            String query = exchange.getRequestURI().getQuery();
            String code  = param(query, "code");
            String error = param(query, "error");

            String html;
            if (code != null && !code.isBlank()) {
                html = successPage();
                codeFuture.complete(code);
            } else {
                html = errorPage(error != null ? error : "Unknown error");
                codeFuture.completeExceptionally(
                        new AuthException("OAuth error: " + error));
            }

            byte[] bytes = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, bytes.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(bytes);
            }

            // Shut down server after a short delay so the browser gets the response
            new Thread(() -> {
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
                server.stop(0);
            }).start();
        });

        server.setExecutor(null);
        server.start();
        logger.info("Auth server listening on localhost:{}{}", PORT, PATH);
    }

    public void stop() {
        if (server != null) server.stop(0);
    }

    /** Blocks until the code arrives or times out (5 minutes) */
    public String waitForCode() throws Exception {
        return codeFuture.get(5, TimeUnit.MINUTES);
    }

    public CompletableFuture<String> getCodeFuture() {
        return codeFuture;
    }

    private String param(String query, String name) {
        if (query == null) return null;
        for (String part : query.split("&")) {
            int eq = part.indexOf('=');
            if (eq < 0) continue;
            if (part.substring(0, eq).equals(name)) {
                try {
                    return URLDecoder.decode(part.substring(eq + 1), StandardCharsets.UTF_8);
                } catch (Exception e) {
                    return part.substring(eq + 1);
                }
            }
        }
        return null;
    }

    private String successPage() {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <title>MineLauncher - Login Successful</title>
                  <style>
                    body { background:#0d1117; color:#e6edf3; font-family:'Segoe UI',sans-serif;
                           display:flex; flex-direction:column; align-items:center;
                           justify-content:center; height:100vh; margin:0; }
                    .card { background:#161b22; border:1px solid #30363d; border-radius:12px;
                            padding:40px 60px; text-align:center; }
                    .icon { font-size:56px; margin-bottom:16px; }
                    h1 { color:#3fb950; margin:0 0 8px; font-size:24px; }
                    p  { color:#8b949e; margin:0; font-size:14px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="icon">✅</div>
                    <h1>Login Successful!</h1>
                    <p>You can close this tab and return to MineLauncher.</p>
                  </div>
                </body>
                </html>
                """;
    }

    private String errorPage(String error) {
        return """
                <!DOCTYPE html>
                <html>
                <head>
                  <meta charset="utf-8">
                  <title>MineLauncher - Login Failed</title>
                  <style>
                    body { background:#0d1117; color:#e6edf3; font-family:'Segoe UI',sans-serif;
                           display:flex; flex-direction:column; align-items:center;
                           justify-content:center; height:100vh; margin:0; }
                    .card { background:#161b22; border:1px solid #30363d; border-radius:12px;
                            padding:40px 60px; text-align:center; }
                    .icon { font-size:56px; margin-bottom:16px; }
                    h1 { color:#f44747; margin:0 0 8px; font-size:24px; }
                    p  { color:#8b949e; margin:0; font-size:14px; }
                  </style>
                </head>
                <body>
                  <div class="card">
                    <div class="icon">❌</div>
                    <h1>Login Failed</h1>
                    <p>%s</p>
                    <p style="margin-top:12px">Please close this tab and try again.</p>
                  </div>
                </body>
                </html>
                """.formatted(error);
    }
}
