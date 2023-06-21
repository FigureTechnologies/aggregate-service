FROM azul/zulu-openjdk:17

ADD ./build/libs/*.jar /service/aggregator-address-loader-job.jar

CMD ["java", "-jar", "/service/aggregator-address-loader-job.jar"]
