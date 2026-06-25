# Dockerfile for n8n with integrated PDF extraction server
FROM n8nio/n8n:latest

# Switch to root to install dependencies
USER root


# Install Node.js packages for PDF server
WORKDIR /home/node/pdf-server

# Copy package.json and install dependencies
COPY package.json .
RUN npm install --production

# Copy PDF server code
COPY pdf-server.js .

# Copy entrypoint script
COPY entrypoint.sh /entrypoint.sh
RUN chmod +x /entrypoint.sh

# Expose both n8n (5678) and PDF server (3001) ports
EXPOSE 5678 3001

# Switch back to node user for security
USER node

# Set working directory back to n8n home
WORKDIR /home/node

# Use custom entrypoint to start PDF server and n8n
ENTRYPOINT ["tini", "--", "/entrypoint.sh"]

