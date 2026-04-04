ARG BASE_IMAGE=node:16
FROM ${BASE_IMAGE}

RUN npm config set registry https://registry.npmmirror.com --global \
    && npm config set fetch-retries 5 --global \
    && npm config set fetch-retry-mintimeout 20000 --global \
    && npm config set fetch-retry-maxtimeout 120000 --global \
    && npm config set fetch-timeout 120000 --global

RUN yarn config set registry https://registry.npmmirror.com --global
