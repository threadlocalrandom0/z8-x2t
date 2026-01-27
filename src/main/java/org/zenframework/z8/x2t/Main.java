package org.zenframework.z8.x2t;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.Iterator;

import static java.nio.charset.StandardCharsets.UTF_8;

public class Main {
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
        e.sendResponseHeaders(200, 0);
        o.write("OK".getBytes(UTF_8));
      } catch (Exception e2) {
        System.err.println("/api/v1/status: " + e2);
        e2.printStackTrace();
        e.sendResponseHeaders(500, -1);
      }
    });
    s.createContext("/api/v1/pdf", e -> {
      try (OutputStream o = e.getResponseBody()) {
        System.err.println("/api/v1/pdf");
        InputStream result = x2t.docx2pdf(e.getRequestBody());
        e.sendResponseHeaders(200, 0);
        byte[] buffer = new byte[8 * 1024];
        int bytesRead;
        while ((bytesRead = result.read(buffer)) != -1) {
          o.write(buffer, 0, bytesRead);
        }
      } catch (Exception e2) {
        System.err.println("/api/v1/pdf: " + e2);
        e2.printStackTrace();
        e.sendResponseHeaders(500, -1);
      }
    });
    s.setExecutor(null);
    System.out.println("starting http server...");
    s.start();
  }
}
