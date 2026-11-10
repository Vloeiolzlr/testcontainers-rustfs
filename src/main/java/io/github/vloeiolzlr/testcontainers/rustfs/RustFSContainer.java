package io.github.vloeiolzlr.testcontainers.rustfs;

import java.time.Duration;

import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.utility.DockerImageName;

/**
 * Testcontainers implementation for <a href="https://rustfs.com">RustFS</a>, an S3-compatible object storage.
 * <p>
 * Exposed ports:
 * <ul>
 * <li>S3 API: 9000</li>
 * <li>Web console: 9001</li>
 * </ul>
 * <pre>{@code
 * RustFSContainer rustfs = new RustFSContainer("rustfs/rustfs:1.0.1");
 * rustfs.start();
 * rustfs.getS3Url();     // http://localhost:55012
 * rustfs.getAccessKey(); // rustfsadmin
 * rustfs.getSecretKey(); // rustfsadmin
 * }</pre>
 */
public class RustFSContainer extends GenericContainer<RustFSContainer> {

	private static final DockerImageName DEFAULT_IMAGE_NAME = DockerImageName.parse("rustfs/rustfs");

	/** S3 API port inside the container */
	public static final int S3_PORT = 9000;

	/** Web console port inside the container */
	public static final int CONSOLE_PORT = 9001;

	/** Access key used when {@link #withAccessKey(String)} is not called */
	public static final String DEFAULT_ACCESS_KEY = "rustfsadmin";

	/** Secret key used when {@link #withSecretKey(String)} is not called */
	public static final String DEFAULT_SECRET_KEY = "rustfsadmin";

	private static final String CONSOLE_PATH = "/rustfs/console/";

	private static final String ACCESS_KEY_ENV = "RUSTFS_ACCESS_KEY";

	private static final String SECRET_KEY_ENV = "RUSTFS_SECRET_KEY";

	/**
	 * Creates a container from an image name.
	 *
	 * @param dockerImageName image name, for example {@code "rustfs/rustfs:1.0.1"}
	 */
	public RustFSContainer(String dockerImageName) {
		this(DockerImageName.parse(dockerImageName));
	}

	/**
	 * Creates a container from an image name.
	 *
	 * @param dockerImageName image name, compatible with {@code rustfs/rustfs}
	 */
	public RustFSContainer(DockerImageName dockerImageName) {
		super(dockerImageName);
		dockerImageName.assertCompatibleWith(DEFAULT_IMAGE_NAME);
		withExposedPorts(S3_PORT, CONSOLE_PORT);
		// Credentials live only in the container environment, so withEnv("RUSTFS_ACCESS_KEY", …) works too
		withEnv(ACCESS_KEY_ENV, DEFAULT_ACCESS_KEY);
		withEnv(SECRET_KEY_ENV, DEFAULT_SECRET_KEY);
		// The port opens a moment before RustFS can serve requests, so wait for the readiness endpoint
		waitingFor(Wait.forHttp("/health/ready").forPort(S3_PORT).withStartupTimeout(Duration.ofMinutes(1)));
	}

	/**
	 * Sets the access key (user name). Default: {@value #DEFAULT_ACCESS_KEY}.
	 *
	 * @param accessKey access key, not blank
	 * @return this container
	 * @throws IllegalArgumentException if the key is null or blank
	 * @throws IllegalStateException if the container is already running
	 */
	public RustFSContainer withAccessKey(String accessKey) {
		return withCredential(ACCESS_KEY_ENV, "accessKey", accessKey);
	}

	/**
	 * Sets the secret key (password). Default: {@value #DEFAULT_SECRET_KEY}.
	 *
	 * @param secretKey secret key, not blank
	 * @return this container
	 * @throws IllegalArgumentException if the key is null or blank
	 * @throws IllegalStateException if the container is already running
	 */
	public RustFSContainer withSecretKey(String secretKey) {
		return withCredential(SECRET_KEY_ENV, "secretKey", secretKey);
	}

	private RustFSContainer withCredential(String env, String name, String value) {
		if (value == null || value.isBlank()) {
			throw new IllegalArgumentException(name + " must not be blank");
		}
		// changing the environment of a running container has no effect, and the getters would no longer match
		if (isRunning()) {
			throw new IllegalStateException(name + " must be set before the container is started");
		}
		return withEnv(env, value);
	}

	/**
	 * Returns the S3 API endpoint reachable from the test.
	 *
	 * @return endpoint, for example {@code http://localhost:55012}
	 */
	public String getS3Url() {
		return String.format("http://%s:%d", getHost(), getMappedPort(S3_PORT));
	}

	/**
	 * Returns the web console address reachable from the test.
	 *
	 * @return console address, for example {@code http://localhost:55013/rustfs/console/}
	 */
	public String getConsoleUrl() {
		// the console port answers 403 at "/"; the UI itself lives under this path
		return String.format("http://%s:%d%s", getHost(), getMappedPort(CONSOLE_PORT), CONSOLE_PATH);
	}

	/**
	 * Returns the access key the container was started with.
	 *
	 * @return access key
	 */
	public String getAccessKey() {
		return getEnvMap().get(ACCESS_KEY_ENV);
	}

	/**
	 * Returns the secret key the container was started with.
	 *
	 * @return secret key
	 */
	public String getSecretKey() {
		return getEnvMap().get(SECRET_KEY_ENV);
	}
}
