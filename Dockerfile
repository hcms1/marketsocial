FROM maven:3.9.11-eclipse-temurin-21 AS build
WORKDIR /app

# Ensure TLS trust store is present/updated for Maven Central downloads
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && update-ca-certificates \
    && rm -rf /var/lib/apt/lists/*

COPY pom.xml .
COPY .mvn .mvn
COPY mvnw mvnw
COPY src src

RUN chmod +x mvnw && ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

# Ensure runtime image also has CA certificates (useful for any outbound HTTPS)
RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates \
    && update-ca-certificates \
    && rm -rf /var/lib/apt/lists/*

RUN mkdir -p /app/uploads

COPY --from=build /app/target/marketsocial-0.0.1-SNAPSHOT.jar /app/app.jar

ENV SERVER_ADDRESS=0.0.0.0
ENV SERVER_PORT=8080
ENV MEDIA_UPLOAD_DIR=/app/uploads

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
