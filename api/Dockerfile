ARG BASE_IMAGE=eclipse-temurin:8-jre-jammy
FROM ${BASE_IMAGE}

## 创建目录，并使用它作为工作目录
RUN mkdir -p /teaching-open-api-server
WORKDIR /teaching-open-api-server
## 将后端项目的 Jar 文件，复制到镜像中
COPY ./jeecg-boot-module-system/target/teaching-open-2.8.0.jar app.jar
ADD ./jeecg-boot-module-system/src/main/resources/application.yml application.yml
ADD ./jeecg-boot-module-system/src/main/resources/application-prod.yml application-prod.yml
ADD ./jeecg-boot-module-system/src/main/resources/logback-spring.xml logback-spring.xml

## 设置 TZ 时区
ENV TZ=Asia/Shanghai
## 设置 JAVA_OPTS 环境变量，可通过 docker run -e "JAVA_OPTS=" 进行覆盖
ENV JAVA_OPTS="-Xms1024m -Xmx1024m -Djava.security.egd=file:/dev/./urandom"

## 暴露后端项目的 8080 端口
EXPOSE 8080

## 启动后端项目
CMD java ${JAVA_OPTS} -jar app.jar ${ARGS}

