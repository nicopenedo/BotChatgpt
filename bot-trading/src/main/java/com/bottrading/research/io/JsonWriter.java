package com.bottrading.research.io;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

public class JsonWriter {

  private final ObjectMapper mapper = new ObjectMapper();

  public void write(Path path, Object value) throws IOException {
    if (path.getParent() != null && !Files.exists(path.getParent())) {
      Files.createDirectories(path.getParent());
    }
    mapper.writerWithDefaultPrettyPrinter().writeValue(path.toFile(), value);
  }
}
