#!/bin/bash

# =============================================================================
# n8n Workflow Management Script
# =============================================================================
# This script handles exporting and importing n8n workflows for the
# TalentPredict project.
#
# Usage:
#   ./manage-workflows.sh export    # Export from running container
#   ./manage-workflows.sh import    # Import into running container
#   ./manage-workflows.sh backup    # Backup and compress workflows
#
# =============================================================================

set -e

# Colors for output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

WORKFLOWS_DIR="n8n-workflows"
CONTAINER_NAME="talentpredict-n8n"
TIMESTAMP=$(date +%Y%m%d_%H%M%S)

# ============================================================================
# Helper Functions
# ============================================================================

log_info() {
  echo -e "${BLUE}[INFO]${NC} $1"
}

log_success() {
  echo -e "${GREEN}[✓]${NC} $1"
}

log_warning() {
  echo -e "${YELLOW}[!]${NC} $1"
}

log_error() {
  echo -e "${RED}[✗]${NC} $1"
}

check_docker() {
  if ! command -v docker &> /dev/null; then
    log_error "Docker is not installed or not in PATH"
    exit 1
  fi
}

check_container_running() {
  if ! docker ps | grep -q "$CONTAINER_NAME"; then
    log_error "Container '$CONTAINER_NAME' is not running"
    log_info "Start the stack with: docker-compose up -d"
    exit 1
  fi
}

create_workflows_dir() {
  if [ ! -d "$WORKFLOWS_DIR" ]; then
    mkdir -p "$WORKFLOWS_DIR"
    log_success "Created directory: $WORKFLOWS_DIR"
  fi
}

# ============================================================================
# Export Workflows
# ============================================================================

export_all_workflows() {
  log_info "Exporting all workflows from '$CONTAINER_NAME'..."

  create_workflows_dir

  # Export all workflows to temporary location in container
  log_info "Executing export command in container..."
  if docker exec "$CONTAINER_NAME" \
    n8n export:workflow --all --output=/tmp/workflows_export.json; then
    log_success "Workflows exported in container"
  else
    log_error "Failed to export workflows"
    exit 1
  fi

  # Copy from container to host
  log_info "Copying workflows to host..."
  EXPORT_FILE="$WORKFLOWS_DIR/workflows_${TIMESTAMP}.json"
  
  if docker cp "$CONTAINER_NAME:/tmp/workflows_export.json" "$EXPORT_FILE"; then
    log_success "Workflows saved to: $EXPORT_FILE"
    
    # Also create a 'latest' symlink/copy
    cp "$EXPORT_FILE" "$WORKFLOWS_DIR/workflows_latest.json"
    log_success "Also saved as: $WORKFLOWS_DIR/workflows_latest.json"
    
    # Display summary
    WORKFLOW_COUNT=$(grep -o '"name":' "$EXPORT_FILE" | wc -l)
    log_info "Total workflows exported: $WORKFLOW_COUNT"
    
  else
    log_error "Failed to copy workflows from container"
    exit 1
  fi

  # Cleanup
  docker exec "$CONTAINER_NAME" rm -f /tmp/workflows_export.json
}

export_single_workflow() {
  local workflow_id=$1
  
  if [ -z "$workflow_id" ]; then
    log_error "Workflow ID not provided"
    echo "Usage: ./manage-workflows.sh export-single <workflow-id>"
    exit 1
  fi

  log_info "Exporting workflow with ID: $workflow_id..."
  
  create_workflows_dir

  # Get workflow name first (for better file naming)
  local workflow_name=$(docker exec "$CONTAINER_NAME" \
    curl -s http://localhost:5678/api/v1/workflows/$workflow_id \
    -H "X-N8N-API-KEY: $N8N_API_KEY" 2>/dev/null | \
    grep -o '"name":"[^"]*' | cut -d'"' -f4 || echo "workflow_$workflow_id")

  EXPORT_FILE="$WORKFLOWS_DIR/${workflow_name}_${workflow_id}.json"

  if docker exec "$CONTAINER_NAME" \
    n8n export:workflow --id="$workflow_id" --output="/tmp/workflow_${workflow_id}.json"; then
    
    docker cp "$CONTAINER_NAME:/tmp/workflow_${workflow_id}.json" "$EXPORT_FILE"
    log_success "Workflow saved to: $EXPORT_FILE"
    
    docker exec "$CONTAINER_NAME" rm -f "/tmp/workflow_${workflow_id}.json"
  else
    log_error "Failed to export workflow $workflow_id"
    exit 1
  fi
}

# ============================================================================
# Import Workflows
# ============================================================================

import_workflows() {
  local import_file=$1

  if [ -z "$import_file" ]; then
    # Try to find latest file
    if [ -f "$WORKFLOWS_DIR/workflows_latest.json" ]; then
      import_file="$WORKFLOWS_DIR/workflows_latest.json"
      log_info "Using latest backup: $import_file"
    else
      log_error "No workflows file specified"
      echo "Usage: ./manage-workflows.sh import <file.json>"
      echo "Available files:"
      ls -lh "$WORKFLOWS_DIR"/*.json 2>/dev/null || echo "  No workflow files found"
      exit 1
    fi
  fi

  if [ ! -f "$import_file" ]; then
    log_error "File not found: $import_file"
    exit 1
  fi

  check_container_running

  log_info "Importing workflows from: $import_file"
  
  # Copy file to container
  if docker cp "$import_file" "$CONTAINER_NAME:/tmp/workflows_import.json"; then
    log_success "File copied to container"
  else
    log_error "Failed to copy file to container"
    exit 1
  fi

  # Import workflows
  if docker exec "$CONTAINER_NAME" \
    n8n import:workflow --input=/tmp/workflows_import.json; then
    log_success "Workflows imported successfully"
  else
    log_error "Failed to import workflows"
    echo "Please check container logs: docker logs $CONTAINER_NAME"
    exit 1
  fi

  # Cleanup
  docker exec "$CONTAINER_NAME" rm -f /tmp/workflows_import.json
  
  log_info "Access n8n at: http://localhost:5678"
}

# ============================================================================
# Backup Workflows
# ============================================================================

backup_workflows() {
  log_info "Creating backup of all workflows with compression..."

  create_workflows_dir

  # First export
  export_all_workflows

  # Then compress
  BACKUP_FILE="n8n-workflows-backup_${TIMESTAMP}.tar.gz"
  
  if tar -czf "$BACKUP_FILE" "$WORKFLOWS_DIR/"; then
    log_success "Backup created: $BACKUP_FILE"
    log_info "Size: $(du -h $BACKUP_FILE | cut -f1)"
  else
    log_error "Failed to create backup"
    exit 1
  fi
}

list_workflows() {
  log_info "Listing workflows in running container..."
  
  check_container_running

  docker exec "$CONTAINER_NAME" n8n export:workflow --all --output=/tmp/list.json && \
  docker exec "$CONTAINER_NAME" cat /tmp/list.json | grep -o '"name":"[^"]*' | cut -d'"' -f4

  docker exec "$CONTAINER_NAME" rm -f /tmp/list.json
}

# ============================================================================
# Main Command Router
# ============================================================================

main() {
  local command=$1
  
  case "$command" in
    export|export-all)
      check_docker
      check_container_running
      export_all_workflows
      ;;
    export-single)
      check_docker
      check_container_running
      export_single_workflow "$2"
      ;;
    import)
      check_docker
      check_container_running
      import_workflows "$2"
      ;;
    backup)
      check_docker
      check_container_running
      backup_workflows
      ;;
    list)
      check_docker
      check_container_running
      list_workflows
      ;;
    help|--help|-h)
      cat << 'EOF'

TalentPredict n8n Workflow Management Script

USAGE:
  ./manage-workflows.sh <command> [options]

COMMANDS:
  export              Export all workflows and save locally
  export-single <id>  Export a specific workflow by ID
  import [file]       Import workflows from file
  backup              Export and compress all workflows
  list                List all workflows in container
  help                Show this help message

EXAMPLES:
  # Export all workflows
  ./manage-workflows.sh export

  # Import from latest backup
  ./manage-workflows.sh import

  # Import from specific file
  ./manage-workflows.sh import n8n-workflows/workflows_20240323_101530.json

  # Create compressed backup
  ./manage-workflows.sh backup

  # List all workflows
  ./manage-workflows.sh list

WORKFLOW FILES:
  Exported workflows are saved in: n8n-workflows/
  - workflows_latest.json (always the most recent export)
  - workflows_<timestamp>.json (timestamped backups)

DOCKER CONTAINER:
  Container Name: talentpredict-n8n
  Web UI: http://localhost:5678
  PDF Server: http://localhost:3001

For more information, see: DOCKER_SETUP.md

EOF
      ;;
    *)
      if [ -z "$command" ]; then
        log_error "No command provided"
      else
        log_error "Unknown command: $command"
      fi
      echo "Use: ./manage-workflows.sh help"
      exit 1
      ;;
  esac
}

# ============================================================================
# Script Entry Point
# ============================================================================

# Show banner
echo -e "${BLUE}"
echo "╔════════════════════════════════════════════════════════════════╗"
echo "║        TalentPredict n8n Workflow Management Tool v1.0         ║"
echo "╚════════════════════════════════════════════════════════════════╝"
echo -e "${NC}"

main "$@"
