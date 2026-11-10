plugins {
	`java-library`   // 다른 프로젝트가 가져다 쓰는 라이브러리
	`maven-publish`  // JitPack · Maven 저장소에 올릴 때 씀
}

group = "io.github.vloeiolzlr"
version = "0.1.0-SNAPSHOT"

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

dependencies {
	// 쓰는 사람에게도 같이 딸려 가야 해서 api (GenericContainer 를 상속하니까)
	api("org.testcontainers:testcontainers:2.0.5")

	testImplementation(platform("org.junit:junit-bom:6.1.3"))
	testImplementation("org.junit.jupiter:junit-jupiter")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testImplementation("org.testcontainers:testcontainers-junit-jupiter:2.0.5")
	testImplementation(platform("software.amazon.awssdk:bom:2.39.6")) // 테스트에서 진짜로 파일 올려 보기
	testImplementation("software.amazon.awssdk:s3")
	testImplementation("org.assertj:assertj-core:3.27.7")
	testRuntimeOnly("org.slf4j:slf4j-simple:2.0.17") // 테스트 중 컨테이너 로그 보기
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
