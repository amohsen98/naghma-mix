# One Dockerfile for all three services:
#   docker build -f backend/Dockerfile --build-arg MODULE=catalog-service backend
FROM maven:3.9-eclipse-temurin-21 AS build
ARG MODULE
WORKDIR /src
COPY pom.xml .
COPY accounts-service/pom.xml accounts-service/
COPY catalog-service/pom.xml catalog-service/
COPY mix-service/pom.xml mix-service/
# cache dependencies separately from sources
RUN mvn -q -B -pl ${MODULE} -am dependency:go-offline -DskipTests || true
COPY accounts-service/src accounts-service/src
COPY catalog-service/src catalog-service/src
COPY mix-service/src mix-service/src
RUN mvn -q -B -pl ${MODULE} -am package -DskipTests

FROM eclipse-temurin:21-jre
ARG MODULE
WORKDIR /app
COPY --from=build /src/${MODULE}/target/quarkus-app/lib/ ./lib/
COPY --from=build /src/${MODULE}/target/quarkus-app/*.jar ./
COPY --from=build /src/${MODULE}/target/quarkus-app/app/ ./app/
COPY --from=build /src/${MODULE}/target/quarkus-app/quarkus/ ./quarkus/
RUN useradd --system --uid 1001 app
USER 1001
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75 -Djava.util.logging.manager=org.jboss.logmanager.LogManager"
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar quarkus-run.jar"]
