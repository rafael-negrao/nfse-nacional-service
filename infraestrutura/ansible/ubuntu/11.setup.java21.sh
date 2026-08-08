#!/bin/bash
# A biblioteca exige Java 21; use quando o servidor ainda não o tiver.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

parse_ambiente_arg "$@"

log_info "=== Setup Java 21 ==="
check_tools
check_dir_exists "./11.setup.java21"

for AMBIENTE in "${AMBIENTES[@]}"; do
    log_info ">>> $AMBIENTE"
    resolver_ambiente "$AMBIENTE"
    fix_key_permissions
    check_ssh
    run_playbook "./11.setup.java21/11.01.instalar-java21.yml" "Instalação Java 21"
    log_info "✓ Java 21 instalado: $AMBIENTE_LABEL ($VM_TARGET)"
    echo ""
done

log_info "=== Java 21 instalado para: ${AMBIENTES[*]} ==="
