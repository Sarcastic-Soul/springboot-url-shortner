FROM node:22-alpine
WORKDIR /app
COPY package.json pnpm-lock.yaml* ./
ENV CI=true
RUN npm install -g pnpm && pnpm install
COPY . .
EXPOSE 3000
CMD ["pnpm", "dev", "--host"]
