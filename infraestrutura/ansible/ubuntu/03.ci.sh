#!/bin/bash
# Deploy da biblioteca: atualiza o código no servidor e instala os artefatos no ~/.m2 local.
set -euo pipefail
source "$(dirname "$0")/lib.sh"

parse_ambiente_arg "$@"

log_info "=== CI — Build e instalação da biblioteca ==="
check_tools
check_dir_exists "./03.ci"
[ "$PULAR_TESTES" = "true" ] && log_warn "Testes serão pulados nesta execução"

for AMBIENTE in "${AMBIENTES[@]}"; do
    log_info ">>> $AMBIENTE"
    resolver_ambiente "$AMBIENTE"
    fix_key_permissions
    check_ssh
    run_playbook "./03.ci/03.01.git_pull_maven_build.yml" "Git Pull + Maven Install" \
        -e "pular_testes=${PULAR_TESTES}"
    log_info "✓ CI concluído: $AMBIENTE_LABEL ($VM_TARGET)"
    echo ""
done

log_info "=== CI finalizado para: ${AMBIENTES[*]} ==="
