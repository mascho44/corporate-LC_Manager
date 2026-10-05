FROM maven:3-eclipse-temurin-26 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

RUN apk add --no-cache poppler-utils tesseract-ocr tesseract-ocr-data-deu tesseract-ocr-data-eng \
    && addgroup -S lcmanager && adduser -S lcmanager -G lcmanager
COPY --from=build /workspace/target/corporate-lc-manager-*.jar app.jar

USER lcmanager
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
