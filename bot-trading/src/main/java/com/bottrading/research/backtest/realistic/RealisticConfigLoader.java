package com.bottrading.research.backtest.realistic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

/** Utility for loading {@link RealisticBacktestConfig} from YAML preset files. */
public final class RealisticConfigLoader {

  private static final ObjectMapper MAPPER = new ObjectMapper(new YAMLFactory());

  private RealisticConfigLoader() {}

  public static RealisticBacktestConfig load(Path path) throws IOException {
    if (path == null || !Files.exists(path)) {
      return new RealisticBacktestConfig();
    }
    return MAPPER.readValue(path.toFile(), RealisticBacktestConfig.class);
  }
}
