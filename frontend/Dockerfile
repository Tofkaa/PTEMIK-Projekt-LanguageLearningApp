# Stage 1: Build the React app (Vite)
FROM node:22.23.4-alpine@sha256:fe4f6d74e20370acb04ad889077d14d0f64a9c8ea6a42bc60cddf1e844699914 AS build
WORKDIR /app

# Függőségek telepítése (cache optimalizálva)
COPY package.json package-lock.json ./
RUN npm ci

# Forráskód másolása és buildelés
COPY . .
ARG VITE_APP_SECURITY_EXERCISE_SALT
ENV VITE_APP_SECURITY_EXERCISE_SALT=$VITE_APP_SECURITY_EXERCISE_SALT
RUN npm run build
RUN npm sbom --omit=dev --sbom-format=cyclonedx > frontend-sbom.json

# Stage 2: Serve with Nginx
FROM nginx:alpine@sha256:a3554c0d0b48177b1413c26e01f695334428020d9df01243fbae3e11cfd17417
RUN apk upgrade --no-cache

RUN rm /etc/nginx/conf.d/default.conf
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Local SSL files are mounted read-only by Docker Compose at runtime.

COPY --from=build /app/dist /usr/share/nginx/html
COPY --from=build /app/frontend-sbom.json /usr/share/sbom/frontend.cdx.json

# Kilógatjuk a 80-as (HTTP) és 443-as (HTTPS) portokat is
EXPOSE 80 443

CMD ["nginx", "-g", "daemon off;"]
