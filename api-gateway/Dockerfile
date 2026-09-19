# Multi-stage build for api-gateway; build from the reactor root so parent pom + common resolve:
#   docker build -f api-gateway/Dockerfile -t uber-api-gateway .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY common ./common
COPY api-gateway ./api-gateway
RUN mvn -q -B -pl api-gateway -am -DskipTests package
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /build/api-gateway/target/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
