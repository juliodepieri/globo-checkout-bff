# Builds either module: docker build --build-arg MODULE=bff -f docker/Dockerfile .
FROM eclipse-temurin:25-jdk AS build
ARG MODULE
WORKDIR /workspace
COPY gradlew settings.gradle build.gradle ./
COPY gradle gradle
COPY bff/build.gradle bff/
COPY mock-services/build.gradle mock-services/
RUN ./gradlew --no-daemon -q :${MODULE}:dependencies > /dev/null
COPY ${MODULE}/src ${MODULE}/src
RUN ./gradlew --no-daemon -q :${MODULE}:bootJar && cp ${MODULE}/build/libs/*.jar /app.jar

FROM eclipse-temurin:25-jre
RUN useradd --system --uid 1001 app
USER app
COPY --from=build /app.jar /app/app.jar
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
