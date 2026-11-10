plugins {
	`java-library`   // 다른 프로젝트가 가져다 쓰는 라이브러리
	`maven-publish`  // JitPack · Maven 저장소에 올릴 때 씀
}

group = "io.github.vloeiolzlr"
version = "0.1.0"

repositories {
	mavenCentral()
}

java {
	withSourcesJar()   // IDE 에서 소스 보기
	withJavadocJar()   // IDE 에서 설명 보기
}

// 내 PC 는 JDK 25 지만, Java 17 프로젝트도 쓸 수 있게 17 기준으로 만듦
tasks.withType<JavaCompile> {
	options.release = 17
	options.encoding = "UTF-8"
}

// 지원하는 가장 낮은 Testcontainers 로 만듦 → 쓰는 사람 프로젝트의 버전(1.x 든 2.x 든)을 그대로 따라감
// 1.21.3 이하는 Docker Engine 29 에서 안 떠서 1.21.4 부터
val testcontainersVersion = "1.21.4"

// 테스트만 다른 버전으로 돌려 보기 (CI): ./gradlew build -PtestcontainersTestVersion=2.0.5
val testcontainersTestVersion = findProperty("testcontainersTestVersion") as String? ?: testcontainersVersion

dependencies {
	// 쓰는 사람에게도 같이 딸려 가야 해서 api (GenericContainer 를 상속하니까)
	api("org.testcontainers:testcontainers:$testcontainersVersion")

	testImplementation(platform("org.junit:junit-bom:6.1.3"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation(platform("software.amazon.awssdk:bom:2.39.6")) // 테스트에서 진짜로 파일 올려 보기
	testImplementation("software.amazon.awssdk:s3")
	testImplementation("org.assertj:assertj-core:3.27.7")
	testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17") // 테스트 중 컨테이너 로그 보기
}

// 테스트할 때만 Testcontainers 버전을 바꿈 (라이브러리 본체는 1.21.4 로 만든 그대로)
listOf("testCompileClasspath", "testRuntimeClasspath").forEach { name ->
	configurations.named(name) {
		resolutionStrategy.force("org.testcontainers:testcontainers:$testcontainersTestVersion")
	}
}

tasks.test {
	useJUnitPlatform()
}

publishing {
	publications {
		create<MavenPublication>("maven") {
			from(components["java"])
		}
	}
}
