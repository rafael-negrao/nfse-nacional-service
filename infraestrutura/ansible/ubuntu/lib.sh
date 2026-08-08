#!/bin/bash
# Funções e constantes compartilhadas entre os scripts Ansible.
# Espelha o lib.sh de nfe-sefaz-sp; os ambientes e as chaves são os mesmos.

# A infraestrutura é do cms-root: é de lá que saem os IPs e as chaves. Esta biblioteca não
# provisiona máquina nenhuma — ela é instalada no ~/.m2 dos servidores que já existem.
TERRAFORM_DIR="../../../../cms-root/infraestrutura/terraform/"
TF_KEYS="../../../../cms-root/infraestrutura/terraform/ec2_instance"
SSH_USER="ubuntu"

RED='\033[0;31m'; GREEN='\033[0;32m'; YELLOW='\033[1;33m'; NC='\033[0m'

log_info()  { echo -e "${GREEN}[INFO]${NC} $1"; }
log_warn()  { echo -e "${YELLOW}[WARN]${NC} $1"; }
log_error() { echo -e "${RED}[ERROR]${NC} $1"; }

check_file_exists() {
    [ -f "$1" ] || { log_error "Arquivo não encontrado: $1"; exit 1; }
}

check_dir_exists() {
    [ -d "$1" ] || { log_error "Diretório não encontrado: $1"; exit 1; }
}

check_tools() {
    for tool in terraform ansible-playbook; do
        command -v "$tool" &>/dev/null || { log_error "$tool não encontrado no PATH"; exit 1; }
    done
}

# O arquivo de variáveis carrega o token do GitHub e não é versionado.
check_vars_file() {
    local arquivo="./${1}_vars_file.yml"
    if [ ! -f "$arquivo" ]; then
        log_error "Arquivo de variáveis ausente: $arquivo"
        log_error "Copie de ${arquivo}.example e preencha o github_token."
        exit 1
    fi
}

fix_key_permissions() {
    local perms
    perms=$(stat -c "%a" "$PRIVATE_KEY" 2>/dev/null || stat -f "%A" "$PRIVATE_KEY" 2>/dev/null)
    [ "$perms" = "600" ] || { log_warn "Ajustando permissões da chave para 600"; chmod 600 "$PRIVATE_KEY"; }
}

check_ssh() {
    if command -v nc &>/dev/null; then
        nc -z -w5 "$VM_TARGET" 22 &>/dev/null \
            && log_info "✓ SSH confirmado em $VM_TARGET" \
            || log_warn "SSH não respondeu em $VM_TARGET — continuando mesmo assim"
    fi
}

# ─────────────────────────────────────────────────────────────────────────────
# resolver_ambiente <hlg_comaho|prd_comaho|prd_mcamas>
# Define: VM_TARGET, PRIVATE_KEY, ANSIBLE_AMBIENTE, AMBIENTE_LABEL
# O IP vem do Terraform do cms-root.
# ─────────────────────────────────────────────────────────────────────────────
resolver_ambiente() {
    local env="${1:-hlg_comaho}"
    local tf_output

    case "$env" in
        hlg_comaho|hml_comaho)
            # DNS = sga-hml-comaho; identificador interno = hlg. Ambos os rótulos valem.
            tf_output="hlg_comaho_ec2_public_ip"
            PRIVATE_KEY="${TF_KEYS}/sga-comaho-hlg-ec2-key.pem"
            ANSIBLE_AMBIENTE="comaho_hlg"
            AMBIENTE_LABEL="Homologação Comaho"
            ;;
        prd_comaho)
            tf_output="prd_comaho_ec2_public_ip"
            PRIVATE_KEY="${TF_KEYS}/sga-comaho-prd-ec2-key.pem"
            ANSIBLE_AMBIENTE="comaho_prd"
            AMBIENTE_LABEL="Produção Comaho"
            ;;
        prd_mcamas)
            tf_output="prd_mcamas_ec2_public_ip"
            PRIVATE_KEY="${TF_KEYS}/sga-mcamas-prd-ec2-key.pem"
            ANSIBLE_AMBIENTE="mcamas_prd"
            AMBIENTE_LABEL="Produção MCamas"
            ;;
        *)
            log_error "Ambiente inválido: '$env'. Use: hlg_comaho | prd_comaho | prd_mcamas"
            exit 1
            ;;
    esac

    VM_TARGET=$(cd "$TERRAFORM_DIR" && terraform output -raw "$tf_output" 2>/dev/null)
    [ -n "$VM_TARGET" ] || { log_error "Não foi possível obter IP via output '$tf_output'"; exit 1; }

    check_vars_file "$ANSIBLE_AMBIENTE"

    log_info "Ambiente : $AMBIENTE_LABEL"
    log_info "IP       : $VM_TARGET"
    log_info "Key      : $PRIVATE_KEY"
}

# ─────────────────────────────────────────────────────────────────────────────
# run_playbook <playbook> <descricao> [extra ansible args...]
# ─────────────────────────────────────────────────────────────────────────────
run_playbook() {
    local playbook="$1"
    local description="$2"
    shift 2

    check_file_exists "$playbook"
    log_info "Executando: $description"

    if ansible-playbook \
        -i "${VM_TARGET}," \
        -u "$SSH_USER" \
        --private-key "$PRIVATE_KEY" \
        -e "ambiente=${ANSIBLE_AMBIENTE}" \
        "$@" \
        "$playbook"; then
        log_info "✓ $description concluído"
    else
        log_error "✗ Falha: $description"
        exit 1
    fi
    echo ""
}

# ─────────────────────────────────────────────────────────────────────────────
# parse_ambiente_arg — lê --ambiente <valor> e --pular-testes dos args
# ─────────────────────────────────────────────────────────────────────────────
AMBIENTES_ALL=(hlg_comaho prd_comaho prd_mcamas)
PULAR_TESTES="false"

parse_ambiente_arg() {
    AMBIENTES=()
    while [[ $# -gt 0 ]]; do
        case "$1" in
            --ambiente)
                shift
                while [[ $# -gt 0 && "$1" != --* ]]; do
                    [[ "$1" == "all" ]] && { AMBIENTES=("${AMBIENTES_ALL[@]}"); shift; break; }
                    AMBIENTES+=("$1"); shift
                done
                ;;
            --pular-testes) PULAR_TESTES="true"; shift ;;
            --help|-h)
                echo "Uso: $0 [--ambiente hlg_comaho|prd_comaho|prd_mcamas|all ...] [--pular-testes]"
                exit 0 ;;
            *) shift ;;
        esac
    done
    [[ ${#AMBIENTES[@]} -gt 0 ]] || AMBIENTES=("hlg_comaho")
}
