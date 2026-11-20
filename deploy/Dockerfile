# Image the CI pipeline builds and deploys (.github/workflows/ci-cd.yml).
#
# Unlike the root Dockerfile (local development), nothing is compiled here: the pipeline has already
# built and tested the jar with ./mvnw, and the build context is a folder holding only that jar.
# So building for the VM's ARM CPU is quick, there is no Maven inside the image to emulate.

# A Java runtime only (no JDK, no Maven). The tag covers both amd64 and arm64.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a normal user instead of root. The uploads folder is created here so the Docker volume
# mounted on it (event posters) belongs to that user.
RUN useradd --system --create-home appuser \
    && mkdir -p /app/uploads \
    && chown appuser /app/uploads
USER appuser

COPY *.jar app.jar

# Kazakhstan time, so @Future checks and LocalDateTime.now() match what users type.
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Almaty"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
