#!/usr/bin/env bash
# Despliega en THREE la última versión de main de caremao/storeManager, con un solo comando:
#
#   /opt/stacks/storemanager/deploy.sh             # lo último de main
#   /opt/stacks/storemanager/deploy.sh --dry-run   # solo dice qué haría
#   /opt/stacks/storemanager/deploy.sh --ref 2c0670c
#
# Pasos: resuelve el commit en GitHub, baja ese código a ~/projects/storemanager y construye
# storemanager:<sha corto> mientras la versión vieja sigue atendiendo. Después para el contenedor,
# respalda data/ en backups/, actualiza compose.yaml si cambió, sube STOREMANAGER_VERSION en .env,
# levanta y espera el healthcheck. Si la versión nueva no queda sana, vuelve sola a la anterior con
# los datos del respaldo. Al final se copia a sí mismo al stack, así la próxima vez corre la última.
#
# Se corre en THREE con el usuario que maneja Docker (no con sudo). Ver README, "Despliegue en THREE".
set -Eeuo pipefail

REPO="${REPO:-caremao/storeManager}"
REF="${REF:-main}"
STACK_DIR="${STACK_DIR:-/opt/stacks/storemanager}"
SRC_DIR="${SRC_DIR:-$HOME/projects/storemanager}"
IMAGE="${IMAGE:-storemanager}"
SERVICE="${SERVICE:-storemanager}"
CONTAINER="${CONTAINER:-storemanager}"
HEALTH_TIMEOUT="${HEALTH_TIMEOUT:-180}"

ENV_FILE="$STACK_DIR/.env"
BACKUP_DIR="$STACK_DIR/backups"
TS="$(date +%Y%m%d-%H%M%S)"
FORCE=""
DRY_RUN=""
OLD=""
DATA_BACKUP=""
COMPOSE_BACKUP=""
# Desde que se levanta la versión nueva, que pudo migrar la base.
STARTED_NEW=""

say() { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
ok() { printf '\033[1;32m ok\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33m !!\033[0m %s\n' "$*" >&2; }
die() { printf '\033[1;31merror:\033[0m %s\n' "$*" >&2; exit 1; }

usage() {
    cat <<EOF
Uso: $(basename "$0") [--ref <rama|tag|sha>] [--force] [--dry-run]

  --ref      Qué desplegar (por defecto: $REF). Con un sha viejo sirve para volver atrás:
             si la imagen ya existe no se reconstruye (pero no restaura datos).
  --force    Rehace el deploy aunque esa versión ya esté corriendo, y reconstruye la imagen.
  --dry-run  Resuelve la versión y dice qué haría, sin tocar nada.

Variables: REPO, STACK_DIR ($STACK_DIR), SRC_DIR ($SRC_DIR), HEALTH_TIMEOUT ($HEALTH_TIMEOUT s).
EOF
}

parse_args() {
    while [ $# -gt 0 ]; do
        case "$1" in
            --ref) [ $# -ge 2 ] || die "--ref necesita un valor"; REF="$2"; shift 2 ;;
            --ref=*) REF="${1#*=}"; shift ;;
            --force) FORCE=1; shift ;;
            --dry-run) DRY_RUN=1; shift ;;
            -h | --help) usage; exit 0 ;;
            *) usage >&2; die "Opción desconocida: $1" ;;
        esac
    done
}

compose() { (cd "$STACK_DIR" && docker compose "$@"); }

# Solo lee claves sueltas del .env (nunca lo ejecuta, y no imprime las claves secretas).
env_get() {
    [ -f "$ENV_FILE" ] || return 0
    sed -n "s/^$1=//p" "$ENV_FILE" | tail -n 1 | tr -d "\"' \r"
}

set_version() {
    local version="$1"
    if [ ! -f "$ENV_FILE" ]; then
        (umask 077 && : >"$ENV_FILE")
    fi
    if grep -q '^STOREMANAGER_VERSION=' "$ENV_FILE"; then
        if [ -n "$version" ]; then
            sed -i "s/^STOREMANAGER_VERSION=.*/STOREMANAGER_VERSION=$version/" "$ENV_FILE"
        else
            sed -i '/^STOREMANAGER_VERSION=/d' "$ENV_FILE"
        fi
    elif [ -n "$version" ]; then
        # Sin salto de línea al final, la clave nueva quedaría pegada a la última.
        if [ -s "$ENV_FILE" ] && [ -n "$(tail -c 1 "$ENV_FILE")" ]; then
            echo >>"$ENV_FILE"
        fi
        echo "STOREMANAGER_VERSION=$version" >>"$ENV_FILE"
    fi
}

running_image() { docker inspect -f '{{.Config.Image}}' "$CONTAINER" 2>/dev/null || true; }

health() {
    docker inspect -f '{{if .State.Health}}{{.State.Health.Status}}{{else}}{{.State.Status}}{{end}}' \
        "$CONTAINER" 2>/dev/null || echo "sin contenedor"
}

# El healthcheck de la imagen pasa a "healthy" cuando /actuator/health responde (unos 30 s).
wait_healthy() {
    local deadline=$((SECONDS + HEALTH_TIMEOUT)) status=""
    while [ "$SECONDS" -lt "$deadline" ]; do
        status="$(health)"
        case "$status" in
            healthy) return 0 ;;
            unhealthy | exited | dead) break ;;
        esac
        sleep 3
    done
    warn "El contenedor quedó en «$status»"
    return 1
}

preflight() {
    if [ "$(id -u)" -eq 0 ] && [ -z "${ALLOW_ROOT:-}" ]; then
        die "Córrelo con tu usuario (el que usa Docker), no como root: los archivos quedarían de root"
    fi
    local cmd
    for cmd in docker curl tar sed cmp; do
        command -v "$cmd" >/dev/null || die "Falta «$cmd»"
    done
    docker info >/dev/null 2>&1 || die "No puedo hablar con Docker (¿tu usuario está en el grupo docker?)"
    docker compose version >/dev/null 2>&1 || die "Falta el plugin «docker compose»"
    [ -f "$STACK_DIR/compose.yaml" ] ||
        die "No encuentro $STACK_DIR/compose.yaml: la primera instalación se hace a mano (README, «Despliegue en THREE»)"
    case "$SRC_DIR" in
        /*/*) ;;
        *) die "SRC_DIR tiene que ser una ruta absoluta de al menos dos niveles, no «$SRC_DIR»" ;;
    esac
    # Se borra y se vuelve a bajar en cada deploy: que sea de verdad la copia del código.
    if [ -e "$SRC_DIR" ] && [ ! -f "$SRC_DIR/Dockerfile" ]; then
        die "$SRC_DIR existe pero no parece una copia de storeManager (no tiene Dockerfile); no lo toco"
    fi
}

resolve_ref() {
    SHA="$(curl -fsSL --max-time 20 -H 'Accept: application/vnd.github.sha' \
        "https://api.github.com/repos/$REPO/commits/$REF")" || die "No pude resolver «$REF» en GitHub ($REPO)"
    [[ "$SHA" =~ ^[0-9a-f]{40}$ ]] || die "GitHub no devolvió un commit para «$REF»"
    TAG="${SHA:0:7}"
    SUBJECT=""
    if command -v jq >/dev/null; then
        SUBJECT="$(curl -fsSL --max-time 20 "https://api.github.com/repos/$REPO/commits/$SHA" |
            jq -r '.commit.message | split("\n")[0]' 2>/dev/null || true)"
    fi
}

fetch_source() {
    local parent tmp
    parent="$(dirname "$SRC_DIR")"
    mkdir -p "$parent"
    tmp="$(mktemp -d "$parent/.storemanager-XXXXXX")"
    say "Bajando el código de $TAG"
    if ! curl -fsSL --max-time 300 "https://github.com/$REPO/archive/$SHA.tar.gz" |
        tar -xz --strip-components=1 -C "$tmp"; then
        rm -rf "$tmp"
        die "No pude bajar el código de $TAG"
    fi
    if [ ! -f "$tmp/Dockerfile" ]; then
        rm -rf "$tmp"
        die "El código de $TAG no trae Dockerfile"
    fi
    rm -rf "$SRC_DIR"
    mv "$tmp" "$SRC_DIR"
}

build_image() {
    if [ -z "$FORCE" ] && docker image inspect "$IMAGE:$TAG" >/dev/null 2>&1; then
        ok "La imagen $IMAGE:$TAG ya existe; no la reconstruyo"
        return
    fi
    local log="$BACKUP_DIR/build-$TS-$TAG.log"
    say "Construyendo $IMAGE:$TAG (lint, tests y build; unos minutos). La versión actual sigue atendiendo."
    if ! docker build --progress=plain --label "org.opencontainers.image.revision=$SHA" \
        -t "$IMAGE:$TAG" "$SRC_DIR" >"$log" 2>&1; then
        tail -n 40 "$log" | sed 's/^/    /' >&2
        die "Falló la construcción; no cambié nada. Log completo: $log"
    fi
    ok "Imagen lista (log: $log)"
}

rollback() {
    trap - ERR
    set +e
    warn "Algo falló al pasar a $TAG; vuelvo a ${OLD:-la versión por defecto del compose}"
    local failed=""
    if [ -n "$STARTED_NEW" ]; then
        warn "Últimas líneas del contenedor:"
        docker logs --tail 40 "$CONTAINER" 2>&1 | sed 's/^/    /' >&2
        compose stop "$SERVICE" >/dev/null 2>&1
        # La versión nueva pudo migrar la base: se repone el respaldo y se guarda la de la falla.
        failed="$BACKUP_DIR/data-fallida-$TS-$TAG"
        mkdir -p "$failed"
        find "$STACK_DIR/data" -mindepth 1 -maxdepth 1 -exec mv -t "$failed" -- {} +
        if ! tar -xzf "$DATA_BACKUP" -C "$STACK_DIR"; then
            die "No pude reponer data/ desde $DATA_BACKUP; no levanto nada (la base de la falla está en $failed)"
        fi
    fi
    if [ -n "$COMPOSE_BACKUP" ]; then
        cp "$COMPOSE_BACKUP" "$STACK_DIR/compose.yaml"
    fi
    set_version "$OLD"
    compose up -d --remove-orphans
    if wait_healthy; then
        die "Volví a ${OLD:-la versión anterior} y está sana.${failed:+ La base con que falló $TAG quedó en $failed}"
    fi
    die "Tampoco arrancó la versión anterior: revisa «docker compose logs» en $STACK_DIR (respaldo: $DATA_BACKUP)"
}

switch_version() {
    local started new_compose=""
    # Si el compose nuevo trae un error, mejor saberlo antes de parar nada.
    if [ -f "$SRC_DIR/deploy/compose.yaml" ] && ! cmp -s "$SRC_DIR/deploy/compose.yaml" "$STACK_DIR/compose.yaml"; then
        new_compose="$SRC_DIR/deploy/compose.yaml"
        docker compose --project-directory "$STACK_DIR" -f "$new_compose" config -q ||
            die "El compose.yaml de $TAG no es válido; no cambié nada"
    fi

    say "Parando $CONTAINER y respaldando data/"
    DATA_BACKUP="$BACKUP_DIR/data-$TS-${OLD:-sin-version}.tar.gz"
    trap rollback ERR
    compose stop "$SERVICE"
    # Con el contenedor parado, el archivo de H2 está cerrado y el respaldo es consistente.
    tar -czf "$DATA_BACKUP" -C "$STACK_DIR" data
    ok "Respaldo: $DATA_BACKUP"

    if [ -n "$new_compose" ]; then
        COMPOSE_BACKUP="$BACKUP_DIR/compose-$TS.yaml"
        cp -p "$STACK_DIR/compose.yaml" "$COMPOSE_BACKUP"
        say "compose.yaml cambió en $TAG (el anterior queda en $COMPOSE_BACKUP):"
        if command -v diff >/dev/null; then
            diff -u "$COMPOSE_BACKUP" "$new_compose" | sed 's/^/    /' || true
        fi
        cp "$new_compose" "$STACK_DIR/compose.yaml"
    fi

    set_version "$TAG"
    started="$(date +%s)"
    say "Levantando $IMAGE:$TAG"
    STARTED_NEW=1
    compose up -d --remove-orphans
    wait_healthy || rollback
    [ "$(running_image)" = "$IMAGE:$TAG" ] || rollback
    trap - ERR
    ok "$CONTAINER está sano con $IMAGE:$TAG"
    docker logs --since "$started" "$CONTAINER" 2>&1 |
        grep -E 'Flyway|Migrating|Successfully (validated|applied)|ERROR' | sed 's/^/    /' || true
}

# La próxima vez corre la versión del script que vino con este código. mv (y no cp) para no
# pisar el archivo que bash puede estar leyendo.
self_update() {
    local fresh="$SRC_DIR/deploy/deploy.sh" target="$STACK_DIR/deploy.sh"
    if [ -f "$fresh" ] && ! cmp -s "$fresh" "$target"; then
        cp "$fresh" "$target.new"
        chmod 755 "$target.new"
        mv -f "$target.new" "$target"
        ok "Actualicé $target"
    fi
}

check_public() {
    local url
    url="$(env_get APP_PUBLIC_BASE_URL)"
    [ -n "$url" ] || return 0
    if curl -fsS --max-time 10 "$url/actuator/health" >/dev/null 2>&1; then
        ok "Responde en $url"
    else
        warn "No pude comprobar $url/actuator/health desde THREE (¿Funnel?). Pruébalo desde el teléfono."
    fi
}

main() {
    parse_args "$@"
    preflight
    resolve_ref
    OLD="$(env_get STOREMANAGER_VERSION)"
    local current
    current="$(running_image)"
    say "$REPO@$REF → $TAG${SUBJECT:+ «$SUBJECT»}"
    say "Corriendo ahora: ${current:-nada} ($(health)); en .env: ${OLD:-sin STOREMANAGER_VERSION}"

    if [ -z "$FORCE" ] && [ "$OLD" = "$TAG" ] && [ "$current" = "$IMAGE:$TAG" ] && [ "$(health)" = "healthy" ]; then
        ok "Ya está desplegada $TAG. Nada que hacer (--force para rehacerlo)."
        exit 0
    fi
    if [ -n "$DRY_RUN" ]; then
        if [ -z "$FORCE" ] && docker image inspect "$IMAGE:$TAG" >/dev/null 2>&1; then
            say "Haría: usar la imagen $IMAGE:$TAG que ya existe"
        else
            say "Haría: bajar $TAG a $SRC_DIR y construir $IMAGE:$TAG"
        fi
        say "Después: parar $CONTAINER, respaldar data/ en $BACKUP_DIR, STOREMANAGER_VERSION=$TAG, levantar y esperar el healthcheck"
        exit 0
    fi

    mkdir -p "$BACKUP_DIR"
    chmod 700 "$BACKUP_DIR"
    if command -v flock >/dev/null; then
        exec 9>"$STACK_DIR/.deploy.lock"
        flock -n 9 || die "Ya hay otro deploy corriendo"
    fi

    fetch_source
    build_image
    switch_version
    self_update
    check_public

    echo
    ok "Listo: ${OLD:-sin versión} → $TAG"
    echo "    La base de antes quedó en $DATA_BACKUP"
    echo "    (para volver atrás, ver README: «Actualizar a la última versión de main»)"
}

main "$@"
exit
