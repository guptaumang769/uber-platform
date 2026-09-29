# Multi-stage build for config-server; build from the reactor root so parent pom + common resolve:
#   docker build -f config-server/Dockerfile -t uber-config-server .
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
COPY common/pom.xml ./common/pom.xml
COPY config-server/pom.xml ./config-server/pom.xml
COPY discovery-server/pom.xml ./discovery-server/pom.xml
COPY api-gateway/pom.xml ./api-gateway/pom.xml
COPY rider-service/pom.xml ./rider-service/pom.xml
COPY driver-service/pom.xml ./driver-service/pom.xml
COPY location-service/pom.xml ./location-service/pom.xml
COPY matching-service/pom.xml ./matching-service/pom.xml
COPY pricing-service/pom.xml ./pricing-service/pom.xml
COPY trip-service/pom.xml ./trip-service/pom.xml
COPY payment-service/pom.xml ./payment-service/pom.xml
COPY notification-service/pom.xml ./notification-service/pom.xml
COPY common ./common
COPY config-server ./config-server
RUN mvn -q -B -pl config-server -am -DskipTests package
FROM eclipse-temurin:21-jre-alpine AS runtime
WORKDIR /app
RUN addgroup -S app && adduser -S app -G app
USER app
COPY --from=build /build/config-server/target/*.jar app.jar
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar app.jar"]
