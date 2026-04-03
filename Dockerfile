# Build stage
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# Run stage
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/matchmaking-engine-0.0.1-SNAPSHOT.jar app.jar
ENTRYPOINT ["java", "-Xms128m", "-Xmx256m", "-jar", "app.jar"]
