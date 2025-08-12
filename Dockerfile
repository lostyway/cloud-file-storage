FROM maven:3.9.8-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .
COPY jwt-security-lib/pom.xml jwt-security-lib/pom.xml
COPY jwt-security-lib/src jwt-security-lib/src
COPY services services

RUN mvn clean package -pl services/cloud-file-storage -am -DskipTests

FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

COPY --from=builder /app/services/cloud-file-storage/target/cloud-file-storage-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8088

ENTRYPOINT ["java", "-jar", "app.jar"]
