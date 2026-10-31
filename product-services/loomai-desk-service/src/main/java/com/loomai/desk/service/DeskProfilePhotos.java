package com.loomai.desk.service;

import java.awt.Color;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Base64;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class DeskProfilePhotos {
  private static final int MAX_BYTES = 256 * 1024;
  private static final int SIZE = 256;

  private DeskProfilePhotos() {}

  public static byte[] normalize(String dataUrl) {
    String prefix;
    if (dataUrl.startsWith("data:image/jpeg;base64,")) prefix = "data:image/jpeg;base64,";
    else if (dataUrl.startsWith("data:image/png;base64,")) prefix = "data:image/png;base64,";
    else throw invalid();
    if (dataUrl.length() > prefix.length() + ((MAX_BYTES + 2) / 3) * 4) throw invalid();
    try {
      byte[] source = Base64.getDecoder().decode(dataUrl.substring(prefix.length()));
      if (source.length == 0 || source.length > MAX_BYTES) throw invalid();
      try (ImageInputStream input =
          ImageIO.createImageInputStream(new ByteArrayInputStream(source))) {
        var readers = ImageIO.getImageReaders(input);
        if (!readers.hasNext()) throw invalid();
        ImageReader reader = readers.next();
        try {
          String format = reader.getFormatName();
          if (!format.equalsIgnoreCase("JPEG") && !format.equalsIgnoreCase("PNG")) throw invalid();
          reader.setInput(input, true, true);
          int width = reader.getWidth(0), height = reader.getHeight(0);
          // Bound decode memory before reading pixels; never persist source metadata or SVG.
          if (width < 1 || height < 1 || width > 1024 || height > 1024) throw invalid();
          BufferedImage decoded = reader.read(0);
          BufferedImage photo = new BufferedImage(SIZE, SIZE, BufferedImage.TYPE_INT_RGB);
          var graphics = photo.createGraphics();
          try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, SIZE, SIZE);
            graphics.setRenderingHint(
                RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int edge = Math.min(width, height);
            int x = (width - edge) / 2, y = (height - edge) / 2;
            graphics.drawImage(decoded, 0, 0, SIZE, SIZE, x, y, x + edge, y + edge, null);
          } finally {
            graphics.dispose();
          }
          ByteArrayOutputStream result = new ByteArrayOutputStream();
          if (!ImageIO.write(photo, "JPEG", result) || result.size() > MAX_BYTES) throw invalid();
          return result.toByteArray();
        } finally {
          reader.dispose();
        }
      }
    } catch (ResponseStatusException e) {
      throw e;
    } catch (Exception e) {
      throw invalid();
    }
  }

  private static ResponseStatusException invalid() {
    return new ResponseStatusException(
        HttpStatus.BAD_REQUEST, "Use a JPEG or PNG photo, at most 256 KB and 1024 pixels per side.");
  }
}
