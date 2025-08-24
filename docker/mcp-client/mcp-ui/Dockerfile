FROM nginx:alpine

# 覆盖默认站点配置
COPY ./default.conf /etc/nginx/conf.d/default.conf

# 拷贝已构建的前端静态资源
COPY ./mcp-ui-dist/ /usr/share/nginx/html/

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
