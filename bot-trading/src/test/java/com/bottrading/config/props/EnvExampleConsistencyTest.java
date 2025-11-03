package com.bottrading.config.props;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.ConfigurationProperties;

class EnvExampleConsistencyTest {

  @Test
  void envExampleContainsAllConfigurationKeys() throws IOException {
    Set<String> expectedKeys = new HashSet<>();
    List<Class<?>> propertiesClasses =
        List.of(BinanceProps.class, TradingProps.class, BanditProps.class, AlertsProps.class, SecurityProps.class);

    for (Class<?> propertiesClass : propertiesClasses) {
      ConfigurationProperties annotation = propertiesClass.getAnnotation(ConfigurationProperties.class);
      assertThat(annotation)
          .withFailMessage("Missing @ConfigurationProperties on %s", propertiesClass.getName())
          .isNotNull();
      collectPropertyEnvKeys(propertiesClass, annotation.prefix(), expectedKeys);
    }

    Set<String> actualKeys =
        Files.lines(Path.of(".env.example"))
            .map(String::trim)
            .filter(line -> !line.isEmpty())
            .filter(line -> !line.startsWith("#"))
            .map(line -> line.split("=", 2)[0])
            .collect(Collectors.toSet());

    for (String expected : expectedKeys) {
      assertThat(actualKeys)
          .withFailMessage("Variable %s missing from .env.example", expected)
          .contains(expected);
    }
  }

  private void collectPropertyEnvKeys(Class<?> type, String prefix, Set<String> result) {
    for (Field field : type.getDeclaredFields()) {
      if (Modifier.isStatic(field.getModifiers())) {
        continue;
      }
      String propertyPath = prefix + "." + toKebabCase(field.getName());
      Class<?> fieldType = field.getType();
      if (isSimpleType(fieldType) || isCollection(fieldType)) {
        result.add(toEnvVariable(propertyPath));
      } else {
        collectPropertyEnvKeys(fieldType, propertyPath, result);
      }
    }
  }

  private boolean isCollection(Class<?> type) {
    return Collection.class.isAssignableFrom(type);
  }

  private boolean isSimpleType(Class<?> type) {
    return type.isPrimitive()
        || Enum.class.isAssignableFrom(type)
        || String.class.equals(type)
        || Number.class.isAssignableFrom(type)
        || BigDecimal.class.equals(type)
        || Boolean.class.equals(type)
        || type.getPackageName().startsWith("java.time");
  }

  private String toKebabCase(String value) {
    return value.replaceAll("([a-z0-9])([A-Z])", "$1-$2").toLowerCase(Locale.ROOT);
  }

  private String toEnvVariable(String propertyPath) {
    return propertyPath.replace('.', '_').replace('-', '_').toUpperCase(Locale.ROOT);
  }
}
