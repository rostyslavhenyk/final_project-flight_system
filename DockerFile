FROM gradle:8.5-jdk17 AS build

WORKDIR /app

COPY . .

RUN gradle clean build --no-daemon

FROM eclipse-temurin:17-jdk

WORKDIR /app

COPY --from=build /app/build/libs/*.jar app.jar

ENV PORT=8080

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]