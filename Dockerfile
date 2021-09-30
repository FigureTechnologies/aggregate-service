FROM openjdk:11

ADD ./build/libs/*.jar /aggregate-service/aggregate-service.jar

CMD ["java", "-jar", "/aggregate-service/aggregate-service.jar"]

HEALTHCHECK --interval=25s --timeout=3s --retries=2 CMD ["java", "-jar", "||", "exit", "1"]
