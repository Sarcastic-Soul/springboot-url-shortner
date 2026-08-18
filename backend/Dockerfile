# Consumes a jar that has already been built:
#
#   ./mvnw -B clean package -DskipTests && docker build -t url-shortener-backend .
#
# CI was compiling the application twice -- once with Maven for the artifact, then again
# inside the image build. This packages the artifact CI already produced, so the image is
# demonstrably the jar that was tested rather than a second, separately compiled one.
# `make images` and `make test-up` build the jar first.

ARG JAR_FILE=target/*.jar

# ---- layer extraction ----
FROM eclipse-temurin:21-jre-alpine AS extractor
ARG JAR_FILE
WORKDIR /app
COPY ${JAR_FILE} app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted --application-filename app.jar

# ---- runtime ----
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S -G app app

# Layer-by-layer copy: dependencies change rarely, application code changes
# every build, so Docker only re-uploads the last layer on a code change.
COPY --from=extractor --chown=app:app /app/extracted/dependencies/ ./
COPY --from=extractor --chown=app:app /app/extracted/spring-boot-loader/ ./
COPY --from=extractor --chown=app:app /app/extracted/snapshot-dependencies/ ./
COPY --from=extractor --chown=app:app /app/extracted/application/ ./

USER app
EXPOSE 8080

# MaxRAMPercentage is essential: without a container memory limit the JVM
# sizes its heap from NODE memory, not the container's share.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["java", "-jar", "app.jar"]
