FROM cgr.dev/chainguard/jre

ENV JDK_JAVA_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+UseParallelGC -XX:ActiveProcessorCount=2"

COPY build/libs/app.jar /app/
WORKDIR /app
ENTRYPOINT ["java", "-jar", "app.jar"]