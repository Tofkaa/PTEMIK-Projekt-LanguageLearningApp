# Stage 1: Build the React app (Vite)
FROM node:22.22.0-alpine@sha256:e4bf2a82ad0a4037d28035ae71529873c069b13eb0455466ae0bc13363826e34 AS build
WORKDIR /app

# Függőségek telepítése (cache optimalizálva)
COPY package.json package-lock.json ./
RUN npm ci

# Forráskód másolása és buildelés
COPY . .
ARG VITE_APP_SECURITY_EXERCISE_SALT
ENV VITE_APP_SECURITY_EXERCISE_SALT=$VITE_APP_SECURITY_EXERCISE_SALT
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine

RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Local SSL files are mounted read-only by Docker Compose at runtime.

COPY --from=build /app/dist /usr/share/nginx/html

# Kilógatjuk a 80-as (HTTP) és 443-as (HTTPS) portokat is
EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
