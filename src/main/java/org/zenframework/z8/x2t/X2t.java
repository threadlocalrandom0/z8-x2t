package org.zenframework.z8.x2t;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

import static java.nio.charset.StandardCharsets.UTF_8;

public final class X2t {
  private static final String XML_TEMPLATE = "<?xml version=\"1.0\" encoding=\"utf-8\"?>\n" +
    "<TaskQueueDataConvert>\n" +
    "  <m_sFileFrom>%1$s/input.docx</m_sFileFrom>\n" +
    "  <m_sFileTo>%1$s/output.pdf</m_sFileTo>\n" +
    "  <m_bDontSaveAdditional>true</m_bDontSaveAdditional>\n" +
    "  <m_sFontDir>%2$s/core-fonts</m_sFontDir>\n" +
    "  <m_sAllFontsPath>%2$s/AllFonts.js</m_sAllFontsPath>\n" +
    "  <m_sTempDir>%3$s</m_sTempDir>\n" +
    "</TaskQueueDataConvert>\n";

  private final Path distDir;
  private final int timeout;

  private X2t(Path distDir, int timeout) {
    this.distDir = distDir;
    this.timeout = timeout;
  }

  public static X2t of(Path distDir, int timeout) {
    distDir = distDir.toAbsolutePath();
    assert Files.exists(distDir.resolve("x2t"));
    assert Files.exists(distDir.resolve("core-fonts"));
    assert Files.exists(distDir.resolve("AllFonts.js"));
    return new X2t(distDir, timeout);
  }

  public InputStream docx2pdf(InputStream source) throws IOException, InterruptedException {
    Path workDir = Files.createTempDirectory(distDir, "x2t-");

    try (OutputStream o = Files.newOutputStream(workDir.resolve("input.docx"))) {
      byte[] buffer = new byte[8 * 1024];
      int bytesRead;
      while ((bytesRead = source.read(buffer)) != -1) {
        o.write(buffer, 0, bytesRead);
      }
    }

    Path tmpDir = workDir.resolve("tmp");
    Files.createDirectory(tmpDir);
    Path x2tXml = workDir.resolve("x2t.xml");
    Files.write(x2tXml, String.format(XML_TEMPLATE, workDir, distDir, tmpDir).getBytes(UTF_8));
    String exe = "x2t" + (System.getProperty("os.name").toLowerCase().contains("win") ? ".exe" : "");
    Process p = new ProcessBuilder(distDir.resolve(exe).toString(), x2tXml.toString())
      .directory(distDir.toFile())
      .start();
    p.waitFor(timeout, TimeUnit.SECONDS);
    if (p.exitValue() != 0)
      throw new IllegalStateException("x2t: exit code " + p.exitValue());

    return Files.newInputStream(workDir.resolve("output.pdf"));
  }
}
