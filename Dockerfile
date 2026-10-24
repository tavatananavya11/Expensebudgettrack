FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /build
COPY pom.xml .
COPY src ./src
RUN mvn -B package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app
RUN groupadd --system spring \
    && useradd --system --gid spring --home-dir /app spring \
    && mkdir -p /data \
    && chown spring:spring /data

COPY --from=build /build/target/expense-budget-tracker-1.0.0.jar /app/app.jar

ENV PORT=8080
ENV DATABASE_URL=jdbc:sqlite:/data/expense-tracker.db

VOLUME ["/data"]
EXPOSE 8080
USER spring:spring

CMD ["sh", "-c", "exec java -jar /app/app.jar --server.port=${PORT:-8080}"]
