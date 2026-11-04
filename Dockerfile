FROM maven:3.9.16-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY pom.xml .
RUN mvn -B dependency:go-offline

COPY src ./src
RUN mvn -B package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN addgroup --system app && adduser --system --ingroup app app
USER app

COPY --from=build /workspace/target/*.jar app.jar

ENV TZ=Europe/Rome

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
