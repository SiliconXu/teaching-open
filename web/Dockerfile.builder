ARG BASE_IMAGE=node:16
FROM ${BASE_IMAGE}

RUN yarn config set registry https://registry.npmmirror.com --global
