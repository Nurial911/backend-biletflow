# ---------- Stage 1: build the .jar ----------
# Uses a big image that has Maven + JDK. It is thrown away after the build.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Copy only pom.xml first and download dependencies.
# Docker caches this step, so rebuilds are fast unless pom.xml changes.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

# Now copy the source code and build the jar (tests skipped for faster image builds).
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---------- Stage 2: the small image that actually runs ----------
# Only a JRE (no Maven, no source code) -> much smaller.
FROM eclipse-temurin:21-jre
WORKDIR /app

# Run as a normal user instead of root (safer).
RUN useradd --system --create-home appuser
USER appuser

COPY --from=build /app/target/*.jar app.jar

# Kazakhstan time, so @Future checks and LocalDateTime.now() match what users type.
ENV JAVA_TOOL_OPTIONS="-Duser.timezone=Asia/Almaty"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
