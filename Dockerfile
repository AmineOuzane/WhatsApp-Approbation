FROM openjdk:17-jdk-alpine

WORKDIR /app

VOLUME /tmp

COPY target/Service-Approbation-WhatsApp-0.0.1-SNAPSHOT.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
