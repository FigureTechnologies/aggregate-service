FROM azul/zulu-openjdk:17

LABEL org.opencontainers.image.source=https://github.com/FigureTechnologies/aggregate-service

ADD ./docker-entrypoint.sh /docker-entrypoint.sh
ADD ./build/libs/*.jar /aggregate-service/aggregate-service.jar

ENTRYPOINT [ "/bin/bash", "-c", "./docker-entrypoint.sh /aggregate-service/aggregate-service.jar" ]
