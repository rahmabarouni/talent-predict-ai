# ============================================
# Stage 1: Build
# ============================================
FROM node:22-alpine AS build

WORKDIR /app

# Copy dependency files first for caching
COPY package.json package-lock.json ./
RUN npm ci --quiet

# Copy source and build
COPY . .
RUN npx ng build --configuration production

# ============================================
# Stage 2: Serve with nginx
# ============================================
FROM nginx:alpine

# Remove default nginx config
RUN rm /etc/nginx/conf.d/default.conf

# Copy custom nginx config
COPY nginx.conf /etc/nginx/conf.d/

# Copy Angular build output
COPY --from=build /app/dist/talent-predict-client/browser /usr/share/nginx/html

EXPOSE 80

CMD ["nginx", "-g", "daemon off;"]
