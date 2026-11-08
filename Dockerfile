# syntax=docker/dockerfile:1

# ---------- Stage 1: build the jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

# Copy only what dependency resolution needs, so this layer stays cached until pom.xml changes.
COPY mvnw pom.xml ./
COPY .mvn .mvn
RUN chmod +x mvnw
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q dependency:go-offline

# Now the source. Tests run in CI with Testcontainers (they need Docker), not here.
COPY src src
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests \
 && cp target/*.jar application.jar

# Split the jar into layers: libraries change rarely, our code changes often.
RUN java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- Stage 2: the image that actually runs ----------
FROM eclipse-temurin:21-jre AS runtime
RUN groupadd --system app && useradd --system --gid app --no-create-home app
WORKDIR /application

COPY --from=build /workspace/extracted/dependencies/ ./
COPY --from=build /workspace/extracted/spring-boot-loader/ ./
COPY --from=build /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build /workspace/extracted/application/ ./

USER app
EXPOSE 8080
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "application.jar"]