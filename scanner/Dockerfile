FROM node:13.12.0-alpine as build
WORKDIR /frontend
COPY . .
RUN npm install
RUN npm run build
FROM nginx:stable-alpine
COPY --from=build /frontend/build /usr/share/nginx/html
COPY --from=build /frontend/nginx/nginx.conf /etc/nginx/conf.d/default.conf
EXPOSE 80
CMD ["nginx", "-g", "daemon off;"]

FROM openjdk:21-slim
COPY target/*.jar app.jar
ENTRYPOINT ["java", "-jar", "/app.jar"]