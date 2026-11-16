# syntax=docker/dockerfile:1

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests \
    && java -Djarmode=tools -jar target/pollenz-backend-*.jar extract --destination /workspace/app

FROM eclipse-temurin:21-jre
RUN groupadd --system pollenz && useradd --system --gid pollenz --no-create-home pollenz
WORKDIR /app
COPY --from=build --chown=pollenz:pollenz /workspace/app/ ./
USER pollenz

# Render injects PORT; server.port reads it and defaults to 8080.
ENV PORT=8080
EXPOSE 8080

# Sized for a 512 MB instance: heap capped well below the container limit, leaving room for
# metaspace, thread stacks and the JVM itself.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65 -XX:+UseSerialGC -Xss512k -XX:MaxMetaspaceSize=128m -XX:+ExitOnOutOfMemoryError"
ENTRYPOINT ["java", "-jar", "pollenz-backend-0.0.1-SNAPSHOT.jar"]
