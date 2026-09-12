# 阶段一：构建前端
FROM node:20-alpine AS frontend
WORKDIR /frontend
COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci
COPY frontend/ ./
RUN npm run build

# 阶段二：构建后端（把前端 dist 打进静态资源）
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml ./
RUN mvn -q dependency:go-offline
COPY backend/src ./src
COPY --from=frontend /frontend/dist ./src/main/resources/static
RUN mvn -q -DskipTests package

# 阶段三：运行（单容器单进程）
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
