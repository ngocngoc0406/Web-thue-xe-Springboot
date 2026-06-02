# Stage 1: Build
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Run
FROM eclipse-temurin:17-jre
WORKDIR /app

# Create uploads directory
RUN mkdir -p /app/uploads

COPY --from=build /app/target/*.jar app.jar

# Railway sets PORT env var automatically
EXPOSE ${PORT:-8080}

ENTRYPOINT ["sh", "-c", "java -jar -Dserver.port=${PORT:-8080} -Dspring.profiles.active=prod app.jar"]
