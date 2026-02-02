package org.zenframework.z8.x2t;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static java.net.HttpURLConnection.*;
import static java.nio.charset.StandardCharsets.UTF_8;

public class Main {
  static final Pattern AMPERSAND = Pattern.compile("&");

  public static void main(String[] args) throws IOException {
    Iterator<String> a = Arrays.asList(args).iterator();
    int port = 8080;
    int timeout = 20;
    Path distDir = null;
    while (a.hasNext()) {
      String option = a.next();
      switch (option) {
        case "--port":
          port = Integer.parseUnsignedInt(a.next());
          break;

        case "--timeout":
          timeout = Integer.parseUnsignedInt(a.next());
          break;

        case "--distdir":
          distDir = Paths.get(a.next());
          break;

        default:
          System.err.println("Unknown option: " + option);
          System.exit(2);
      }
    }

    if (distDir == null) {
      System.err.println("Missing option: --distdir");
      System.exit(2);
    }

    X2t x2t = X2t.of(distDir, timeout);
    HttpServer s = HttpServer.create(new InetSocketAddress(port), 0);
    s.createContext("/api/v1/status", e -> {
      try (OutputStream o = e.getResponseBody()) {
        e.sendResponseHeaders(HTTP_OK, 0);
        o.write("OK".getBytes(UTF_8));
      } catch (Exception e2) {
        System.err.println("/api/v1/status: " + e2);
        e2.printStackTrace();
        e.sendResponseHeaders(HTTP_INTERNAL_ERROR, -1);
      }
    });
    s.createContext("/api/v1/convert", e -> {
      try (OutputStream o = e.getResponseBody()) {
        String from;
        String to;

        String q = e.getRequestURI().getQuery();
        if (!Objects.equals(e.getRequestMethod(), "POST")) {
          e.sendResponseHeaders(HTTP_BAD_METHOD, -1);
          return;
        } else if (q == null) {
          e.sendResponseHeaders(HTTP_BAD_REQUEST, -1);
          return;
        } else {
          Map<String, String> query = AMPERSAND.splitAsStream(q).collect(
            Collectors.toMap(
              it -> it.indexOf('=') == -1 ? it : it.substring(0, it.indexOf('=')),
              it -> it.indexOf('=') == -1 ? "" : it.substring(it.indexOf('=') + 1)
            )
          );
          from = query.get("from");
          to = query.get("to");
          if (from == null || !X2t.EXTENSIONS_WHITELIST.contains(from)
            || to == null || !X2t.EXTENSIONS_WHITELIST.contains(to)) {
            e.sendResponseHeaders(HTTP_BAD_REQUEST, -1);
            return;
          }
        }
        System.err.println("/api/v1/convert");
        InputStream result = x2t.run(e.getRequestBody(), from, to);

        e.sendResponseHeaders(HTTP_OK, 0);
        byte[] buffer = new byte[8 * 1024];
        int bytesRead;
        while ((bytesRead = result.read(buffer)) != -1) {
          o.write(buffer, 0, bytesRead);
        }
      } catch (Exception e2) {
        System.err.println("/api/v1/convert: " + e2);
        e2.printStackTrace();
        e.sendResponseHeaders(HTTP_INTERNAL_ERROR, -1);
      }
    });
    s.setExecutor(null);
    System.out.println("starting http server...");
    s.start();
  }
}
