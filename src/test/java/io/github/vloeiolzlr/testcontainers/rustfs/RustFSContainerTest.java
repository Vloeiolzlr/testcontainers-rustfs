package io.github.vloeiolzlr.testcontainers.rustfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

class RustFSContainerTest {

	/** CI also runs these tests against rustfs/rustfs:latest through the RUSTFS_IMAGE variable */
	private static final String IMAGE = System.getenv().getOrDefault("RUSTFS_IMAGE", "rustfs/rustfs:1.0.1");

	@Test
	void putAndGetObjectWithDefaultCredentials() {
		try (RustFSContainer rustfs = new RustFSContainer(IMAGE)) {
			rustfs.start();

			assertThat(rustfs.getAccessKey()).isEqualTo(RustFSContainer.DEFAULT_ACCESS_KEY);
			try (S3Client s3 = s3(rustfs, rustfs.getAccessKey(), rustfs.getSecretKey())) {
				s3.createBucket(b -> b.bucket("test-bucket"));
				s3.putObject(r -> r.bucket("test-bucket").key("hello.txt"), RequestBody.fromString("hello rustfs"));

				String body = s3.getObjectAsBytes(r -> r.bucket("test-bucket").key("hello.txt")).asUtf8String();
				assertThat(body).isEqualTo("hello rustfs");
			}
		}
	}

	@Test
	void customCredentials() {
		try (RustFSContainer rustfs = new RustFSContainer(IMAGE).withAccessKey("my-user").withSecretKey("my-secret-key")) {
			rustfs.start();

			try (S3Client s3 = s3(rustfs, "my-user", "my-secret-key")) {
				s3.createBucket(b -> b.bucket("custom"));
				assertThat(s3.listBuckets().buckets()).anyMatch(b -> b.name().equals("custom"));
			}
			// the default credentials must no longer work
			try (S3Client wrong = s3(rustfs, RustFSContainer.DEFAULT_ACCESS_KEY, RustFSContainer.DEFAULT_SECRET_KEY)) {
				assertThatThrownBy(wrong::listBuckets).isInstanceOf(S3Exception.class);
			}
		}
	}

	@Test
	void credentialsFromWithEnvAreKept() {
		// people used to Testcontainers often set the environment directly
		try (RustFSContainer rustfs = new RustFSContainer(IMAGE).withEnv("RUSTFS_ACCESS_KEY", "env-user")
				.withEnv("RUSTFS_SECRET_KEY", "env-secret")) {
			rustfs.start();

			assertThat(rustfs.getAccessKey()).isEqualTo("env-user");
			assertThat(rustfs.getSecretKey()).isEqualTo("env-secret");
			try (S3Client s3 = s3(rustfs, "env-user", "env-secret")) {
				s3.createBucket(b -> b.bucket("from-env"));
			}
		}
	}

	@Test
	void credentialsCannotChangeAfterStart() {
		try (RustFSContainer rustfs = new RustFSContainer(IMAGE)) {
			rustfs.start();

			assertThatThrownBy(() -> rustfs.withAccessKey("late-user")).isInstanceOf(IllegalStateException.class);
			assertThatThrownBy(() -> rustfs.withSecretKey("late-secret")).isInstanceOf(IllegalStateException.class);
			assertThat(rustfs.getAccessKey()).isEqualTo(RustFSContainer.DEFAULT_ACCESS_KEY);
		}
	}

	@Test
	void rejectsBlankCredentials() {
		RustFSContainer rustfs = new RustFSContainer(IMAGE);

		assertThatThrownBy(() -> rustfs.withAccessKey(null)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> rustfs.withAccessKey(" ")).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> rustfs.withSecretKey("")).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void consoleIsReachable() throws Exception {
		try (RustFSContainer rustfs = new RustFSContainer(IMAGE)) {
			rustfs.start();

			assertThat(rustfs.getConsoleUrl()).endsWith("/rustfs/console/");
			HttpResponse<String> res = HttpClient.newHttpClient()
					.send(HttpRequest.newBuilder(URI.create(rustfs.getConsoleUrl())).build(), HttpResponse.BodyHandlers.ofString());
			assertThat(res.statusCode()).isEqualTo(200);
		}
	}

	@Test
	void rejectsIncompatibleImage() {
		assertThatThrownBy(() -> new RustFSContainer("postgres:18")).isInstanceOf(IllegalStateException.class);
	}

	private static S3Client s3(RustFSContainer rustfs, String accessKey, String secretKey) {
		return S3Client.builder()
				.endpointOverride(URI.create(rustfs.getS3Url()))
				.region(Region.US_EAST_1)
				.credentialsProvider(StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secretKey)))
				.forcePathStyle(true)
				.build();
	}
}
