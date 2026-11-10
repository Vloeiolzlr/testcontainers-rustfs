# testcontainers-rustfs

[![CI](https://github.com/Vloeiolzlr/testcontainers-rustfs/actions/workflows/ci.yml/badge.svg)](https://github.com/Vloeiolzlr/testcontainers-rustfs/actions/workflows/ci.yml)
[![JitPack](https://jitpack.io/v/Vloeiolzlr/testcontainers-rustfs.svg)](https://jitpack.io/#Vloeiolzlr/testcontainers-rustfs)

[Testcontainers](https://java.testcontainers.org/) module for [RustFS](https://github.com/rustfs/rustfs),
an S3-compatible object storage. Use it to test code that talks to S3 (AWS S3, Cloudflare R2, …)
against a real server.

MinIO is no longer actively developed, and the Testcontainers team is
[not adding new modules](https://github.com/testcontainers/testcontainers-java/issues/11322). This module
is a drop-in style replacement for `MinIOContainer`.

## Requirements

- Java 17+
- Testcontainers 2.x
- Docker
- Tested with `rustfs/rustfs:1.0.1`

## Install

Gradle (Kotlin DSL):

```kotlin
repositories {
    mavenCentral()
    maven("https://jitpack.io")
}

dependencies {
    testImplementation("com.github.Vloeiolzlr:testcontainers-rustfs:v0.1.0")
}
```

Maven:

```xml
<repositories>
    <repository>
        <id>jitpack.io</id>
        <url>https://jitpack.io</url>
    </repository>
</repositories>

<dependency>
    <groupId>com.github.Vloeiolzlr</groupId>
    <artifactId>testcontainers-rustfs</artifactId>
    <version>v0.1.0</version>
    <scope>test</scope>
</dependency>
```

## Usage

```java
try (RustFSContainer rustfs = new RustFSContainer("rustfs/rustfs:1.0.1")) {
    rustfs.start();

    S3Client s3 = S3Client.builder()
            .endpointOverride(URI.create(rustfs.getS3Url()))
            .region(Region.US_EAST_1)
            .credentialsProvider(StaticCredentialsProvider.create(
                    AwsBasicCredentials.create(rustfs.getAccessKey(), rustfs.getSecretKey())))
            .forcePathStyle(true) // RustFS serves buckets as paths: http://host:port/bucket/key
            .build();

    s3.createBucket(b -> b.bucket("my-bucket"));
}
```

Custom credentials (default: `rustfsadmin` / `rustfsadmin`):

```java
new RustFSContainer("rustfs/rustfs:1.0.1")
        .withAccessKey("my-user")
        .withSecretKey("my-secret-key");
```

### JUnit 5

```java
@Testcontainers
class UploadTest {

    @Container
    static RustFSContainer rustfs = new RustFSContainer("rustfs/rustfs:1.0.1");

    @Test
    void upload() {
        String endpoint = rustfs.getS3Url();
        // ...
    }
}
```

### Spring Boot

```java
@TestConfiguration(proxyBeanMethods = false)
class StorageTestConfig {

    @Bean
    RustFSContainer rustfs() {
        return new RustFSContainer("rustfs/rustfs:1.0.1");
    }

    @Bean
    DynamicPropertyRegistrar storageProperties(RustFSContainer rustfs) {
        return registry -> {
            registry.add("app.storage.endpoint", rustfs::getS3Url);
            registry.add("app.storage.access-key", rustfs::getAccessKey);
            registry.add("app.storage.secret-key", rustfs::getSecretKey);
        };
    }
}
```

## API

| Method | Description |
| --- | --- |
| `getS3Url()` | S3 API endpoint, e.g. `http://localhost:55012` |
| `getConsoleUrl()` | Web console address |
| `getAccessKey()` / `getSecretKey()` | Credentials the container started with |
| `withAccessKey(String)` / `withSecretKey(String)` | Set credentials before start |

The container waits for `/health/ready` before it reports as started, so tests do not hit
RustFS while it is still booting.

## Migrating from MinIOContainer

| MinIOContainer | RustFSContainer |
| --- | --- |
| `new MinIOContainer("minio/minio:…")` | `new RustFSContainer("rustfs/rustfs:1.0.1")` |
| `withUserName(…)` | `withAccessKey(…)` |
| `withPassword(…)` | `withSecretKey(…)` |
| `getS3URL()` | `getS3Url()` |
| `getUserName()` / `getPassword()` | `getAccessKey()` / `getSecretKey()` |

## License

[MIT](LICENSE)
