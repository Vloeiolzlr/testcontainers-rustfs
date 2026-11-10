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
- Testcontainers 1.21.4+ or 2.x (works with Spring Boot 3 and 4). The library does not force a
  Testcontainers version on you; your project's version is used. Testcontainers 1.21.3 and older
  cannot talk to Docker Engine 29.
- Docker
- RustFS image `1.0.0` or newer (old `1.0.0-alpha` images do not start this way).
  Tested in CI with `rustfs/rustfs:1.0.1` and `rustfs/rustfs:latest`, on Testcontainers 1.21.4 and 2.0.5

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

Custom credentials (default: `rustfsadmin` / `rustfsadmin`). Set them before `start()`:

```java
new RustFSContainer("rustfs/rustfs:1.0.1")
        .withAccessKey("my-user")
        .withSecretKey("my-secret-key");
```

`withEnv("RUSTFS_ACCESS_KEY", …)` / `withEnv("RUSTFS_SECRET_KEY", …)` work as well, and so do the
names you may have from MinIO: `MINIO_ROOT_USER` / `MINIO_ROOT_PASSWORD` (or `RUSTFS_ROOT_USER` /
`RUSTFS_ROOT_PASSWORD`). `getAccessKey()` / `getSecretKey()` always return what the container was
started with.

### Image from a mirror registry

An image that is not named `rustfs/rustfs` is rejected, so a typo cannot start some other server.
For a company mirror, mark it as a substitute:

```java
new RustFSContainer(DockerImageName.parse("mirror.example.com/rustfs/rustfs:1.0.1")
        .asCompatibleSubstituteFor("rustfs/rustfs"));
```

### From another container

`getS3Url()` is for the test JVM (`localhost` + a random port). When another container talks to
RustFS, put both on one network and use the alias with port `9000`:

```java
Network network = Network.newNetwork();

RustFSContainer rustfs = new RustFSContainer("rustfs/rustfs:1.0.1")
        .withNetwork(network)
        .withNetworkAliases("rustfs");

GenericContainer<?> app = new GenericContainer<>("my-app:latest")
        .withNetwork(network)
        .withEnv("S3_ENDPOINT", "http://rustfs:9000");
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

## Troubleshooting

| Symptom | Cause and fix |
| --- | --- |
| `Bucket name should be between 3 and 63 characters long` or `The specified bucket is not valid` | The S3 client uses virtual-hosted style (`bucket.localhost`). Turn on path style: `forcePathStyle(true)` (AWS SDK v2), `withPathStyleAccessEnabled(true)` (v1), `spring.cloud.aws.s3.path-style-access-enabled=true` (Spring Cloud AWS). |
| `The Access Key Id you provided does not exist in our records` (403) | The client uses other credentials than the container. Pass `getAccessKey()` / `getSecretKey()` to the client. |
| `IllegalStateException: … must be set before the container is started` | `withAccessKey` / `withSecretKey` was called after `start()`. Move it before `start()`. |
| Start times out after 1 minute | The image is older than `1.0.0`, or Docker is not running. |
| `Failed to verify that image '…' is a compatible substitute for 'rustfs/rustfs'` | The image comes from another registry. See [Image from a mirror registry](#image-from-a-mirror-registry). |

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
