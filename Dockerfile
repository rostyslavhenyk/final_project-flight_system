FROM gradle:8.5-jdk21 AS build

WORKDIR /
COPY . .

RUN gradle clean build --no-daemon

FROM eclipse-temurin:21-jdk

WORKDIR /

RUN apt-get update && apt-get install -y sqlite3 && rm -rf /var/lib/apt/lists/*

COPY --from=build /build/libs/*.jar app.jar

ENV PORT=8080
EXPOSE 8080

CMD ["java", "-jar", "app.jar"]