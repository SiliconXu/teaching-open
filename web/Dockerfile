ARG BASE_IMAGE=nginx
FROM ${BASE_IMAGE}
VOLUME /tmp
ENV LANG en_US.UTF-8
RUN mkdir -p /usr/share/nginx/html
RUN rm -rf /etc/nginx/conf.d/default.conf
ADD nginx/ /etc/nginx/conf.d/
ADD dist/ /usr/share/nginx/html/
EXPOSE 80
