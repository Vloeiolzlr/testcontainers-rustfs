package io.github.vloeiolzlr.testcontainers.rustfs;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.net.URI;

import org.junit.jupiter.api.Test;

import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.S3Exception;

class RustFSContainerTest {

	private static final String IMAGE = "rustfs/rustfs:1.0.1";

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
