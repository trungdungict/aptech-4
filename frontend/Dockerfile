# Multi-stage React production image: Vite build → nginx static + /api proxy.
# Compose build context is the repository root (see docker-compose.yml) so
# deploy/nginx/local-stack.conf is available. Vite bake-time only — no runtime env mutation.
#
# Default VITE_API_BASE_URL=/api/v1 → same-origin browser calls via nginx → api:8080.

FROM node:22.14.0-alpine AS build
WORKDIR /app

ARG VITE_API_BASE_URL=/api/v1
ARG VITE_PRODUCT_IMAGE_MAX_SIZE_BYTES=2097152
ARG VITE_PRODUCT_IMAGE_ALLOWED_TYPES=image/jpeg,image/png,image/webp
ENV VITE_API_BASE_URL=${VITE_API_BASE_URL}
ENV VITE_PRODUCT_IMAGE_MAX_SIZE_BYTES=${VITE_PRODUCT_IMAGE_MAX_SIZE_BYTES}
ENV VITE_PRODUCT_IMAGE_ALLOWED_TYPES=${VITE_PRODUCT_IMAGE_ALLOWED_TYPES}

COPY frontend/package.json frontend/package-lock.json ./
RUN npm ci

COPY frontend/ ./
RUN npm run build

FROM nginx:1.27.4-alpine AS runtime
COPY --from=build /app/dist /usr/share/nginx/html
COPY deploy/nginx/local-stack.conf /etc/nginx/conf.d/default.conf

EXPOSE 80

HEALTHCHECK --interval=10s --timeout=3s --start-period=5s --retries=5 \
	CMD wget -qO- http://127.0.0.1/ >/dev/null || exit 1
