# --- ETAPA 1: BUILD ---
FROM maven:3.9.15-eclipse-temurin-25 AS builder
WORKDIR /app
COPY . .
RUN mvn clean package -DskipTests

# --- ETAPA 2: RUN TIME ---

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=builder /app/target/*.jar product.jar
ENTRYPOINT ["java","-jar","product.jar"]
