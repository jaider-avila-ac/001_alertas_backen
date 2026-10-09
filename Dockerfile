# backend en dos etapas: se compila con maven y la imagen final solo lleva el jre y el jar

# ---- compilar ----
FROM maven:3.9-eclipse-temurin-21 AS compilar
WORKDIR /app

# primero solo el pom: si no cambian las dependencias, docker reutiliza esta capa
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q -DskipTests package && cp target/*.jar app.jar

# ---- correr ----
FROM eclipse-temurin:21-jre

# la hora de "hoy", el cierre de matriculas y las estadisticas por mes se calculan en el servidor
ENV TZ=America/Bogota
# la jvm toma la memoria segun lo que tenga el contenedor, no un numero fijo
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75 -XX:+ExitOnOutOfMemoryError"

# sin root
RUN groupadd --system alertas && useradd --system --gid alertas --no-create-home alertas
WORKDIR /app
COPY --from=compilar /app/app.jar app.jar
USER alertas

EXPOSE 8085
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
