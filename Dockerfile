# ---- build ----
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -q -DskipTests compile
RUN mvn -B -q dependency:copy-dependencies -DincludeScope=runtime -DoutputDirectory=target/lib

# ---- run ----
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/classes ./classes
COPY --from=build /app/target/lib ./lib
EXPOSE 10000
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=70", "-XX:+UseSerialGC", "-cp", "classes:lib/*", "com.expensetracker.App"]