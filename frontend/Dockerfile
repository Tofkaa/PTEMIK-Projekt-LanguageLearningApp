# Stage 1: Build the React app (Vite)
FROM node:20-alpine AS build
WORKDIR /app

# Függőségek telepítése (cache optimalizálva)
COPY package.json package-lock.json ./
RUN npm ci

# Forráskód másolása és buildelés
COPY . .
RUN npm run build

# Stage 2: Serve with Nginx
FROM nginx:alpine

RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

# SSL tanúsítványok bemásolása az Nginx konténerbe
COPY ssl /etc/nginx/ssl

COPY --from=build /app/dist /usr/share/nginx/html

# Kilógatjuk a 80-as (HTTP) és 443-as (HTTPS) portokat is
EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]