FROM maven:3.9-eclipse-temurin-26 AS build
WORKDIR /workspace

COPY pom.xml .
COPY src ./src
RUN --mount=type=cache,target=/root/.m2 mvn -B clean package -DskipTests

FROM eclipse-temurin:25-jre-alpine
WORKDIR /app

RUN apk add --no-cache poppler-utils tesseract-ocr tesseract-ocr-data-deu tesseract-ocr-data-eng tesseract-ocr-data-osd tesseract-ocr-data-chi_sim \
    && addgroup -S lcmanager && adduser -S lcmanager -G lcmanager \
    && mkdir -p /data/intake /data/swift && chown -R lcmanager:lcmanager /data
# The accurate "best" OCR models replace the fast ones of the Alpine packages (better on scans: digits, decimal separators). Pinned commit, checked by SHA-256.
ARG TESSDATA_BEST=e12c65a915945e4c28e237a9b52bc4a8f39a0cec
RUN set -eu; cd /usr/share/tessdata; \
    for entry in eng:8280aed0782fe27257a68ea10fe7ef324ca0f8d85bd2fd145d1c2b560bcb66ba deu:8407331d6aa0229dc927685c01a7938fc5a641d1a9524f74838cdac599f0d06e chi_sim:4fef2d1306c8e87616d4d3e4c6c67faf5d44be3342290cf8f2f0f6e3aa7e735b; do \
      lang="${entry%%:*}"; sum="${entry##*:}"; \
      wget -q -O "$lang.traineddata.tmp" "https://github.com/tesseract-ocr/tessdata_best/raw/${TESSDATA_BEST}/$lang.traineddata"; \
      echo "$sum  $lang.traineddata.tmp" | sha256sum -c -; \
      mv "$lang.traineddata.tmp" "$lang.traineddata"; \
    done
COPY --from=build /workspace/target/corporate-lc-manager-*.jar app.jar

USER lcmanager
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
