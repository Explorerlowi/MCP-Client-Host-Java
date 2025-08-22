FROM nginx:alpine

# 健康检查工具
RUN apk add --no-cache curl

# 覆盖默认站点配置
COPY ./default.conf /etc/nginx/conf.d/default.conf

# 拷贝已构建的前端静态资源
COPY ./mcp-ui-dist/ /usr/share/nginx/html/

EXPOSE 80

HEALTHCHECK --interval=30s --timeout=5s --start-period=5s --retries=3 \
  CMD curl -fsS http://localhost/ || exit 1

CMD ["nginx", "-g", "daemon off;"]
