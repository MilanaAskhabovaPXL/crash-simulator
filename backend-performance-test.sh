#!/bin/bash

# ================================================================
# Backend Performance Comparison: OpenObserve vs Grafana LGTM Stack
# Meet: query snelheid, resource gebruik, storage
# ================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

RESULTS_FILE="backend-comparison.txt"
rm -f "$RESULTS_FILE"

print_header() {
    echo -e "${BLUE}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║   Backend Performance: OpenObserve vs Grafana LGTM Stack       ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

# ================================================================
# Helper: meet N keer dezelfde query en geef gemiddelde terug
# ================================================================
measure_query() {
    local label="$1"
    local n="$2"
    shift 2
    local cmd=("$@")

    local total=0
    for i in $(seq 1 "$n"); do
        local t_start=$(date +%s%3N)
        "${cmd[@]}" > /dev/null 2>&1 || true
        local t_end=$(date +%s%3N)
        total=$((total + t_end - t_start))
    done

    local avg=$((total / n))
    printf "    %-35s %6dms  (n=%d)\n" "$label" "$avg" "$n"
    local key="${label//[^a-zA-Z0-9]/_}"
    echo "${key}=$avg" >> "$RESULTS_FILE"
}

# ================================================================
# Stap 1: Grafana Stack
# ================================================================
test_grafana_backend() {
    echo -e "${YELLOW}[1/3] GRAFANA STACK backend performance...${NC}"
    echo ""

    if ! curl -sf "http://localhost:3000/api/health" > /dev/null 2>&1; then
        echo -e "  ${RED}✗ Grafana niet actief op :3000 — sla over${NC}"
        echo ""
        return
    fi

    echo "  Prometheus queries:"
    measure_query "Prometheus - up query" 100 \
        curl -sf "http://localhost:9090/api/v1/query?query=up"

    measure_query "Prometheus - rate query" 100 \
        curl -sf "http://localhost:9090/api/v1/query?query=rate(otelcol_exporter_sent_spans_total%5B1m%5D)"

    echo ""
    echo "  Loki queries:"
    measure_query "Loki - labels" 100 \
        curl -sf "http://localhost:3100/loki/api/v1/labels"

    measure_query "Loki - log query" 50 \
        curl -sf "http://localhost:3100/loki/api/v1/query_range?query=%7Bservice_name%3D%22crash-simulator%22%7D&limit=10&start=$(($(date +%s) - 3600))000000000&end=$(date +%s)000000000"

    echo ""
    echo "  Tempo queries:"
    measure_query "Tempo - search" 50 \
        curl -sf "http://localhost:3200/api/search?limit=5"

    echo ""
    echo "  Resource gebruik (containers):"
    docker stats --no-stream \
        --format "    {{.Name}}: CPU={{.CPUPerc}} MEM={{.MemUsage}}" \
        crash-simulator-prometheus-1 \
        crash-simulator-loki-1 \
        crash-simulator-tempo-1 \
        crash-simulator-grafana-1 2>/dev/null || true

    echo ""
    echo "GRAFANA_TESTED=1" >> "$RESULTS_FILE"
}

# ================================================================
# Stap 2: OpenObserve
# ================================================================
test_openobserve_backend() {
    echo -e "${YELLOW}[2/3] OPENOBSERVE backend performance...${NC}"
    echo ""

    if ! curl -sf "http://localhost:5080/healthz" > /dev/null 2>&1; then
        echo -e "  ${RED}✗ OpenObserve niet actief op :5080 — sla over${NC}"
        echo ""
        return
    fi

    local AUTH="admin@example.com:admin123"

    echo "  OpenObserve queries:"
    measure_query "OpenObserve - streams list" 100 \
        curl -sf -u "$AUTH" "http://localhost:5080/api/default/streams"

    measure_query "OpenObserve - logs search" 50 \
        curl -sf -u "$AUTH" -X POST "http://localhost:5080/api/default/_search" \
            -H "Content-Type: application/json" \
            -d '{"query":{"sql":"SELECT * FROM default LIMIT 5","from":0,"size":5}}'

    measure_query "OpenObserve - traces search" 50 \
        curl -sf -u "$AUTH" -X POST "http://localhost:5080/api/default/_search" \
            -H "Content-Type: application/json" \
            -d '{"query":{"sql":"SELECT * FROM default_traces LIMIT 5","from":0,"size":5}}'

    echo ""
    echo "  Resource gebruik (container):"
    docker stats --no-stream \
        --format "    {{.Name}}: CPU={{.CPUPerc}} MEM={{.MemUsage}}" \
        crash-simulator-openobserve-1 2>/dev/null || true

    echo ""
    echo "OPENOBSERVE_TESTED=1" >> "$RESULTS_FILE"
}

# ================================================================
# Stap 3: Vergelijkingsrapport
# ================================================================
generate_report() {
    echo -e "${YELLOW}[3/3] Vergelijkingsrapport...${NC}"
    source "$RESULTS_FILE" 2>/dev/null || true
    echo ""

    echo -e "${GREEN}╔════════════════════════════════════════════════════════════════╗${NC}"
    echo -e "${GREEN}║                BACKEND VERGELIJKING                             ║${NC}"
    echo -e "${GREEN}╚════════════════════════════════════════════════════════════════╝${NC}"
    echo ""

    echo -e "${BLUE}Query snelheid (ms, lager = beter):${NC}"
    printf "  %-40s %8s\n" "Prometheus - up query"           "${Prometheus___up_query:-N/A}ms"
    printf "  %-40s %8s\n" "Prometheus - rate query"         "${Prometheus___rate_query:-N/A}ms"
    printf "  %-40s %8s\n" "Loki - labels"                   "${Loki___labels:-N/A}ms"
    printf "  %-40s %8s\n" "Loki - log query"                "${Loki___log_query:-N/A}ms"
    printf "  %-40s %8s\n" "Tempo - search"                  "${Tempo___search:-N/A}ms"
    printf "  %-40s %8s\n" "OpenObserve - streams list"      "${OpenObserve___streams_list:-N/A}ms"
    printf "  %-40s %8s\n" "OpenObserve - logs search"       "${OpenObserve___logs_search:-N/A}ms"
    printf "  %-40s %8s\n" "OpenObserve - traces search"     "${OpenObserve___traces_search:-N/A}ms"

    echo ""
    echo -e "${BLUE}Architectuur vergelijking:${NC}"
    echo "  Grafana Stack : 4 containers (Prometheus + Loki + Tempo + Grafana)"
    echo "  OpenObserve   : 1 container (alles in één)"
    echo ""
    echo -e "${BLUE}Query talen:${NC}"
    echo "  Grafana Stack : PromQL (metrics) + LogQL (logs) + TraceQL (traces)"
    echo "  OpenObserve   : SQL voor alles"
    echo ""

    echo "Rapport opgeslagen in: $RESULTS_FILE"
}

# ================================================================
# Main
# ================================================================
print_header
test_grafana_backend
test_openobserve_backend
generate_report

echo ""
echo -e "${GREEN}Klaar!${NC}"
