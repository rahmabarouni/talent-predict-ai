# n8n Custom Docker Image with Integrated PDF Extraction

This directory contains the custom Docker configuration for n8n with an integrated PDF extraction server.

## 📁 Directory Structure

```
n8n-custom/
├── Dockerfile          # Custom n8n image with PDF server
├── entrypoint.sh       # Startup script for both services
├── package.json        # npm dependencies for PDF server
├── pdf-server.js       # Express.js PDF extraction API
└── README.md           # This file
```

## 🔧 Components

### Dockerfile

Builds a custom n8n image that includes:

- **Base**: `n8nio/n8n:latest`
- **PDF Server**: Express.js app on port 3001
- **Dependencies**: Node.js packages for PDF parsing
- **Ports Exposed**: 5678 (n8n) + 3001 (PDF Server)

### entrypoint.sh

Startup script that:

1. Starts PDF extraction server in background
2. Waits for PDF server to be ready
3. Launches n8n in foreground (allows Docker to manage process)

### pdf-server.js

Express.js server providing:

**Endpoints:**

- `GET /health` - Health check
- `POST /extract` - PDF text extraction

**Features:**

- Multipart file upload support
- PDF text and metadata extraction using `pdf-parse`
- Error handling and validation
- Max file size: 50MB

### package.json

npm dependencies:

- `express` - Web framework
- `multer` - File upload handling
- `pdf-parse` - PDF text extraction

## 🏗️ Build Process

When `docker-compose build n8n` is executed:

1. **Stage 1**: Pull `n8nio/n8n:latest` base image
2. **Stage 2**: Switch to root user for package installation
3. **Stage 3**: Copy `package.json` and run `npm install`
4. **Stage 4**: Copy `pdf-server.js` and `entrypoint.sh`
5. **Stage 5**: Expose ports 5678 and 3001
6. **Stage 6**: Set up entrypoint to run both services
7. **Stage 7**: Switch back to `node` user for security

## 🚀 Runtime Behavior

When the container starts:

1. **Initialization**
   - Read environment variables for PostgreSQL config
   - Initialize n8n database (if first run)

2. **Services Start**

   ```
   entrypoint.sh (runs as PID 1)
   ├── node pdf-server.js (background, PID 2)
   │   └── Listens on :3001
   └── n8n start (foreground, PID 3)
       └── Listens on :5678
   ```

3. **Healthcheck**
   - Docker monitors: `curl -f http://localhost:3001/health`
   - Container restarts if health check fails 5 times

## 📝 Environment Variables

Configured in `docker-compose.yml`:

```yaml
DB_TYPE: postgresdb
DB_POSTGRESDB_HOST: postgres
DB_POSTGRESDB_PORT: 5432
DB_POSTGRESDB_DATABASE: n8n
DB_POSTGRESDB_USER: postgres
DB_POSTGRESDB_PASSWORD: "11111111"
N8N_ENCRYPTION_KEY: <value-from-.env>
```

## 🔌 Ports

| Port | Service    | Purpose                      |
| ---- | ---------- | ---------------------------- |
| 5678 | n8n        | Web UI and webhook endpoints |
| 3001 | PDF Server | PDF text extraction API      |

## 🐛 Troubleshooting

### PDF Server Not Starting

```bash
# Check logs
docker logs talentpredict-n8n

# Test PDF server directly
docker exec talentpredict-n8n curl http://localhost:3001/health

# Check if node process is running
docker exec talentpredict-n8n ps aux | grep node
```

### n8n Not Responding

```bash
# Check n8n logs
docker logs talentpredict-n8n | tail -100

# Test n8n endpoint
curl http://localhost:5678

# Check database connection
docker exec talentpredict-n8n n8n health-check
```

### Database Connection Issues

```bash
# Test PostgreSQL connectivity from container
docker exec talentpredict-n8n pg_isready -h postgres -p 5432 -U postgres

# Check database exists
docker exec talentpredict-db psql -U postgres -l | grep n8n

# View n8n logs for DB errors
docker logs talentpredict-n8n | grep -i "database\|postgres"
```

## 🔄 Rebuilding the Image

```bash
# Rebuild this custom image
docker-compose build talentpredict-n8n

# Rebuild and restart
docker-compose up -d --build talentpredict-n8n

# Rebuild without cache (fresh dependencies)
docker-compose build --no-cache talentpredict-n8n
```

## 📊 Image Size

The built image is approximately:

- **Base n8n image**: ~500MB
- **PDF server additions**: ~50MB
- **Total**: ~550MB

## 🔒 Security Considerations

1. **User Switching**: Dockerfile switches from `root` to `node` user
2. **Secrets**: N8N_ENCRYPTION_KEY must be strong and unique
3. **File Limits**: PDF uploads capped at 50MB
4. **Network**: Runs on bridge network, isolated from host network

## 📦 Extending the Image

To add additional dependencies or tools:

1. Edit `package.json` to add npm packages
2. Edit `Dockerfile` to add system packages (in `USER root` section)
3. Edit `entrypoint.sh` to start additional services
4. Rebuild: `docker-compose build talentpredict-n8n`

### Example: Adding Another Service

```javascript
// Add to entrypoint.sh
node /home/node/another-service/server.js &
ANOTHER_PID=$!
echo "✓ Another Service started (PID: $ANOTHER_PID)"
```

## 🔗 Integration Points

### Backend to n8n

```
Backend → n8n webhook
URL: http://talentpredict-n8n:5678/webhook/master-agent
Port: 5678 (internal Docker network)
```

### Backend to PDF Server

```
Backend → PDF Extractor
URL: http://talentpredict-n8n:3001/extract
Port: 3001 (internal Docker network)
```

### External Access

```
Browser → n8n
URL: http://localhost:5678 (from host machine)
Port: 5678 (localhost, port-forwarded)

Browser → PDF Server (typically via n8n workflows)
URL: http://localhost:3001 (from host machine)
Port: 3001 (localhost, port-forwarded)
```

## 📚 References

- **n8n Documentation**: https://docs.n8n.io
- **Docker Multi-Service**: https://docs.docker.com/config/containers/multi-service_container/
- **Express.js Documentation**: https://expressjs.com
- **pdf-parse Documentation**: https://github.com/modeindustries/pdf-parse

## 🆘 Support

For issues specific to this custom configuration:

1. Check Docker logs: `docker logs talentpredict-n8n`
2. Review this README and referenced documentation
3. Test services individually (n8n vs PDF server)
4. See main `DOCKER_SETUP.md` for general troubleshooting
