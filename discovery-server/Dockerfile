# Multi-stage build for discovery-server; build from the reactor root so parent pom + common resolve:
#   docker build -f discovery-server/Dockerfile -t uber-discovery-server .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY common ./common
COPY discovery-server ./discovery-server
RUN mvn -q -B -pl discovery-server -am -DskipTests package
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /build/discovery-server/target/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
