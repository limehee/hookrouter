#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
VERSION="${HOOKROUTER_VERSION:-$(grep '^version=' "${ROOT_DIR}/gradle.properties" | cut -d'=' -f2)}"
SPRING_BOOT_VERSION="${SPRING_BOOT_VERSION:-4.0.2}"

if ! command -v mvn >/dev/null 2>&1; then
  echo "mvn is required but was not found in PATH"
  exit 1
fi

TMP_DIR="$(mktemp -d)"
trap 'rm -rf "${TMP_DIR}"' EXIT

echo "[consumer-smoke] publish artifacts to mavenLocal (${VERSION})"
"${ROOT_DIR}/gradlew" --no-daemon --configuration-cache publishToMavenLocal

echo "[consumer-smoke] create Maven consumer project"
MAVEN_DIR="${TMP_DIR}/consumer-maven-core"
mkdir -p "${MAVEN_DIR}/src/test/java/com/example"
cat > "${MAVEN_DIR}/pom.xml" <<EOF_POM
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 http://maven.apache.org/xsd/maven-4.0.0.xsd">
  <modelVersion>4.0.0</modelVersion>
  <groupId>com.example</groupId>
  <artifactId>consumer-maven-core</artifactId>
  <version>1.0.0</version>
  <properties>
    <maven.compiler.source>21</maven.compiler.source>
    <maven.compiler.target>21</maven.compiler.target>
    <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    <junit.version>5.13.4</junit.version>
  </properties>
  <dependencies>
    <dependency>
      <groupId>io.github.limehee</groupId>
      <artifactId>hookrouter-core</artifactId>
      <version>${VERSION}</version>
    </dependency>
    <dependency>
      <groupId>org.junit.jupiter</groupId>
      <artifactId>junit-jupiter</artifactId>
      <version>\${junit.version}</version>
      <scope>test</scope>
    </dependency>
  </dependencies>
  <build>
    <plugins>
      <plugin>
        <groupId>org.apache.maven.plugins</groupId>
        <artifactId>maven-surefire-plugin</artifactId>
        <version>3.5.4</version>
      </plugin>
    </plugins>
  </build>
</project>
EOF_POM

cat > "${MAVEN_DIR}/src/test/java/com/example/CoreConsumerSmokeTest.java" <<'EOF_TEST1'
package com.example;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.NotificationTypeDefinition;
import io.github.limehee.hookrouter.core.registry.NotificationTypeRegistry;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreConsumerSmokeTest {

    @Test
    void usesPublishedCoreArtifact() {
        NotificationTypeRegistry registry = new NotificationTypeRegistry();
        NotificationTypeDefinition definition = NotificationTypeDefinition.builder()
            .typeId("demo.smoke.event")
            .title("Smoke")
            .defaultMessage("Smoke event")
            .category("general")
            .build();

        registry.register(definition);
        assertTrue(registry.contains("demo.smoke.event"));

        Notification<String> notification = Notification.of("demo.smoke.event", "general", "payload");
        assertEquals("demo.smoke.event", notification.getTypeId());
    }
}
EOF_TEST1

echo "[consumer-smoke] run Maven consumer test"
mvn -f "${MAVEN_DIR}/pom.xml" -q test

echo "[consumer-smoke] create Gradle consumer project"
GRADLE_DIR="${TMP_DIR}/consumer-gradle-spring"
mkdir -p "${GRADLE_DIR}/src/main/java/com/example"
mkdir -p "${GRADLE_DIR}/src/test/java/com/example"
mkdir -p "${GRADLE_DIR}/src/test/resources"

cat > "${GRADLE_DIR}/settings.gradle" <<'EOF_SETTINGS'
rootProject.name = 'consumer-gradle-spring'
EOF_SETTINGS

cat > "${GRADLE_DIR}/build.gradle" <<EOF_GRADLE
plugins {
    id 'java'
    id 'org.springframework.boot' version '${SPRING_BOOT_VERSION}'
}

group = 'com.example'
version = '1.0.0'

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenLocal()
    mavenCentral()
}

dependencies {
    implementation 'org.springframework.boot:spring-boot-starter:${SPRING_BOOT_VERSION}'
    implementation 'io.github.limehee:hookrouter-spring:${VERSION}'
    testImplementation 'org.springframework.boot:spring-boot-starter-test:${SPRING_BOOT_VERSION}'
}

tasks.withType(Test).configureEach {
    useJUnitPlatform()
}
EOF_GRADLE

cat > "${GRADLE_DIR}/src/main/java/com/example/ConsumerApplication.java" <<'EOF_APP'
package com.example;

import io.github.limehee.hookrouter.core.domain.FormatterKey;
import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.core.domain.NotificationTypeDefinition;
import io.github.limehee.hookrouter.core.domain.WebhookFormatter;
import io.github.limehee.hookrouter.core.port.WebhookSender;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class ConsumerApplication {

    @Bean
    NotificationTypeDefinition smokeTypeDefinition() {
        return NotificationTypeDefinition.builder()
            .typeId("demo.smoke.event")
            .title("Smoke Event")
            .defaultMessage("Smoke Event")
            .category("general")
            .build();
    }

    @Bean
    WebhookFormatter<String, String> smokeFormatter() {
        return new WebhookFormatter<>() {
            @Override
            public FormatterKey key() {
                return FormatterKey.of("test", "demo.smoke.event");
            }

            @Override
            public Class<String> contextClass() {
                return String.class;
            }

            @Override
            public String format(Notification<String> notification) {
                return notification.getContext();
            }
        };
    }

    @Bean
    TestWebhookSender testWebhookSender() {
        return new TestWebhookSender();
    }

    static class TestWebhookSender implements WebhookSender {
        private final AtomicInteger sendCount = new AtomicInteger();

        @Override
        public String platform() {
            return "test";
        }

        @Override
        public SendResult send(String webhookUrl, Object payload) {
            sendCount.incrementAndGet();
            return SendResult.success(200);
        }

        int count() {
            return sendCount.get();
        }
    }
}
EOF_APP

cat > "${GRADLE_DIR}/src/test/resources/application.yml" <<'EOF_YAML'
hookrouter:
  platforms:
    test:
      endpoints:
        main:
          url: "https://example.com/webhook"
  default-mappings:
    - platform: "test"
      webhook: "main"
EOF_YAML

cat > "${GRADLE_DIR}/src/test/java/com/example/StarterConsumerSmokeTest.java" <<'EOF_TEST2'
package com.example;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import io.github.limehee.hookrouter.core.domain.Notification;
import io.github.limehee.hookrouter.spring.publisher.NotificationPublisher;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class StarterConsumerSmokeTest {

    @Autowired
    private NotificationPublisher publisher;

    @Autowired
    private ConsumerApplication.TestWebhookSender sender;

    @Test
    void invokesWebhookPipelineFromPublishedSpringArtifact() {
        publisher.publish(Notification.of("demo.smoke.event", "general", "hello"));

        await()
            .atMost(Duration.ofSeconds(5))
            .untilAsserted(() -> assertThat(sender.count()).isEqualTo(1));
    }
}
EOF_TEST2

echo "[consumer-smoke] run Gradle consumer test"
"${ROOT_DIR}/gradlew" --no-daemon -p "${GRADLE_DIR}" test

echo "[consumer-smoke] success"
