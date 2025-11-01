package com.bottrading.research.io;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

public class CsvWriter {

  public void write(Path path, List<String[]> rows) throws IOException {
    if (rows == null || rows.isEmpty()) {
      return;
    }
    if (path.getParent() != null && !Files.exists(path.getParent())) {
      Files.createDirectories(path.getParent());
    }
    StringBuilder builder = new StringBuilder();
    for (String[] row : rows) {
      builder.append(String.join(",", row)).append('\n');
    }
    Files.writeString(path, builder.toString());
  }
}
