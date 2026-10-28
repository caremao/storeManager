# syntax=docker/dockerfile:1

# 1) SPA de React
FROM node:22-alpine AS frontend
WORKDIR /app
COPY frontend/package.json frontend/package-lock.json* ./
RUN if [ -f package-lock.json ]; then npm ci; else npm install; fi
COPY frontend/ ./
# Si el lint o los tests fallan, la imagen no se construye.
RUN npm run lint && npm test && npm run build

# 2) Backend con la SPA dentro (Spring Boot la sirve desde /static)
FROM maven:3.9-amazoncorretto-21 AS backend
WORKDIR /build
COPY backend/pom.xml ./
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /app/dist ./src/main/resources/static
RUN mvn -B -q package -DskipTests

# 3) Imagen final
FROM amazoncorretto:21-alpine
WORKDIR /app
COPY --from=backend /build/target/storemanager-*.jar app.jar
ENV APP_DATA_DIR=/data \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"
VOLUME /data
EXPOSE 8080
# Sin root por defecto; el compose de THREE fija 1000:1884 como el resto de los stacks.
USER 1000:1000
HEALTHCHECK --interval=30s --timeout=5s --start-period=40s \
    CMD wget -qO- http://127.0.0.1:8080/actuator/health > /dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
