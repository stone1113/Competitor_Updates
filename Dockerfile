FROM eclipse-temurin:11-jre-focal

LABEL maintainer="NEV-Insight"

WORKDIR /app

COPY nev-admin/target/nev-admin-1.0.0-SNAPSHOT.jar app.jar

EXPOSE 8090

ENV JAVA_OPTS="-Xms256m -Xmx512m -Duser.timezone=Asia/Shanghai"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
