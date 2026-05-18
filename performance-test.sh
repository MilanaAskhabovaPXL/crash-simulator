#!/bin/bash

# ================================================================
# Performance Test: OpenObserve vs Grafana LGTM Stack
# Project: crash-simulator
# Endpoints: /batch/run-success | /batch/run-crash | /batch/run-silent-failure
# ================================================================

set -e

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

APP_URL="http://localhost:8080"
NUM_REQUESTS=500
RESULTS_FILE="test-results.txt"
REPORT_FILE="performance-report.txt"

print_header() {
    echo -e "${BLUE}╔════════════════════════════════════════════════════════╗${NC}"
    echo -e "${BLUE}║   Performance Test: OpenObserve vs Grafana LGTM Stack  ║${NC}"
    echo -e "${BLUE}╚════════════════════════════════════════════════════════╝${NC}"
    echo ""
}

# ================================================================
# Stap 1: Check welke stacks draaien
# ================================================================
check_stacks() {
    echo -e "${YELLOW}[1/6] Stack status controleren...${NC}"

    GRAFANA_UP=0
    OPENOBSERVE_UP=0
    APP_UP=0

    curl -sf "$APP_URL/actuator/health" > /dev/null 2>&1 && APP_UP=1
    curl -sf "http://localhost:3000/api/health" > /dev/null 2>&1 && GRAFANA_UP=1
    curl -sf "http://localhost:5080/healthz" > /dev/null 2>&1 && OPENOBSERVE_UP=1

    [ $APP_UP -eq 1 ]          && echo -e "  ${GREEN}✓ App draait op :8080${NC}"       || echo -e "  ${RED}✗ App draait NIET${NC}"
    [ $GRAFANA_UP -eq 1 ]      && echo -e "  ${GREEN}✓ Grafana Stack actief${NC}"       || echo -e "  ${RED}✗ Grafana Stack niet actief${NC}"
    [ $OPENOBSERVE_UP -eq 1 ]  && echo -e "  ${GREEN}✓ OpenObserve actief${NC}"         || echo -e "  ${RED}✗ OpenObserve niet actief${NC}"
    echo ""

    if [ $APP_UP -eq 0 ]; then
        echo -e "${RED}ERROR: App draait niet. Start met: docker-compose up -d app${NC}"
        exit 1
    fi
}

# ================================================================
# Helper: resource snapshot via docker stats
# ================================================================
get_docker_stats() {
    # Geeft: CPU% en MEM voor alle relevante containers
    docker stats --no-stream --format "{{.Name}},{{.CPUPerc}},{{.MemUsage}}" 2>/dev/null \
        | grep -E "loki|tempo|prometheus|grafana|openobserve|otel-collector|app" \
        | sed 's/%//g'
}

# ================================================================
# Helper: send N requests en meet latency
# ================================================================
run_load_test() {
    local label="$1"
    local endpoint="$2"
    local n="$3"

    local success=0 error=0 total_ms=0
    declare -a latencies=()

    for i in $(seq 1 $n); do
        local t_start=$(date +%s%3N)
        if curl -sf -X POST "$APP_URL$endpoint" > /dev/null 2>&1; then
            success=$((success + 1))
        else
            error=$((error + 1))
        fi
        local t_end=$(date +%s%3N)
        local ms=$((t_end - t_start))
        total_ms=$((total_ms + ms))
        latencies+=($ms)
        [ $((i % 100)) -eq 0 ] && echo -e "    ${CYAN}$i/$n requests...${NC}"
    done

    local avg=$((total_ms / n))

    IFS=$'\n' sorted=($(sort -n <<<"${latencies[*]}")); unset IFS
    local p50=${sorted[$((n / 2))]}
    local p95=${sorted[$((n * 95 / 100))]}
    local p99=${sorted[$((n * 99 / 100))]}

    echo "  Gem: ${avg}ms  |  P50: ${p50}ms  |  P95: ${p95}ms  |  P99: ${p99}ms"
    echo "  Succes: $success  Fouten: $error"

    # Sla op voor rapport
    local key="${label//[^a-zA-Z0-9]/_}"
    echo "${key}_AVG=$avg"       >> "$RESULTS_FILE"
    echo "${key}_P50=$p50"       >> "$RESULTS_FILE"
    echo "${key}_P95=$p95"       >> "$RESULTS_FILE"
    echo "${key}_P99=$p99"       >> "$RESULTS_FILE"
    echo "${key}_SUCCESS=$success" >> "$RESULTS_FILE"
    echo "${key}_ERROR=$error"   >> "$RESULTS_FILE"
}

# ================================================================
# Stap 2: Baseline (OTel agent uitgeschakeld via env)
# ================================================================
test_baseline() {
    echo -e "${YELLOW}[2/6] BASELINE test (eerste meting, monitoring actief)...${NC}"
    echo "  Sending $NUM_REQUESTS requests naar /batch/run-success..."
    echo ""
    run_load_test "BASELINE" "/batch/run-success" "$NUM_REQUESTS"
    echo ""
}

# ================================================================
# Stap 3: Met volledige stack (LGTM + OpenObserve)
# ================================================================
test_with_monitoring() {
    echo -e "${YELLOW}[3/6] TEST met monitoring actief (LGTM + OpenObserve)...${NC}"
    echo "  Sending $NUM_REQUESTS requests naar /batch/run-success..."
    echo ""

    local stats_before
    stats_before=$(get_docker_stats)

    run_load_test "MONITORED" "/batch/run-success" "$NUM_REQUESTS"

    local stats_after
    stats_after=$(get_docker_stats)

    echo ""
    echo "  Container resource gebruik tijdens test:"
    echo "$stats_after" | while IFS=',' read -r name cpu mem; do
        printf "    %-45s CPU: %6s  MEM: %s\n" "$name" "$cpu%" "$mem"
    done
    echo ""

    echo "STATS_DURING_TEST<<EOF" >> "$RESULTS_FILE"
    echo "$stats_after" >> "$RESULTS_FILE"
    echo "EOF" >> "$RESULTS_FILE"
}

# ================================================================
# Stap 4: Crash scenario test
# ================================================================
test_crash_scenario() {
    echo -e "${YELLOW}[4/6] CRASH scenario test...${NC}"
    echo "  Sending 100 requests naar /batch/run-crash..."
    echo ""
    run_load_test "CRASH" "/batch/run-crash" 100
    echo ""
}

# ================================================================
# Stap 5: Query performance
# ================================================================
test_query_performance() {
    echo -e "${YELLOW}[5/6] QUERY performance meten...${NC}"
    echo ""

    if [ $GRAFANA_UP -eq 1 ]; then
        echo "  Grafana Stack queries:"

        local t=$(date +%s%3N)
        curl -sf "http://localhost:9090/api/v1/query?query=up" > /dev/null
        local prom_ms=$(( $(date +%s%3N) - t ))
        printf "    %-30s %6dms\n" "Prometheus (up)" "$prom_ms"
        echo "QUERY_PROMETHEUS_MS=$prom_ms" >> "$RESULTS_FILE"

        t=$(date +%s%3N)
        curl -sf "http://localhost:3100/loki/api/v1/labels" > /dev/null
        local loki_ms=$(( $(date +%s%3N) - t ))
        printf "    %-30s %6dms\n" "Loki (labels)" "$loki_ms"
        echo "QUERY_LOKI_MS=$loki_ms" >> "$RESULTS_FILE"

        t=$(date +%s%3N)
        curl -sf "http://localhost:3200/api/search?limit=5" > /dev/null 2>&1 || true
        local tempo_ms=$(( $(date +%s%3N) - t ))
        printf "    %-30s %6dms\n" "Tempo (trace search)" "$tempo_ms"
        echo "QUERY_TEMPO_MS=$tempo_ms" >> "$RESULTS_FILE"

        echo ""
    fi

    if [ $OPENOBSERVE_UP -eq 1 ]; then
        echo "  OpenObserve queries:"

        local t=$(date +%s%3N)
        curl -sf -u "admin@example.com:admin123" \
            "http://localhost:5080/api/default/streams" > /dev/null 2>&1 || true
        local oo_ms=$(( $(date +%s%3N) - t ))
        printf "    %-30s %6dms\n" "OpenObserve (streams)" "$oo_ms"
        echo "QUERY_OPENOBSERVE_MS=$oo_ms" >> "$RESULTS_FILE"

        echo ""
    fi
}

# ================================================================
# Stap 6: Rapport genereren
# ================================================================
generate_report() {
    echo -e "${YELLOW}[6/6] Rapport genereren...${NC}"
    source "$RESULTS_FILE" 2>/dev/null || true

    {
        echo "========================================================"
        echo "  PERFORMANCE RAPPORT: crash-simulator"
        echo "  Datum: $(date)"
        echo "========================================================"
        echo ""
        echo "--- LATENCY VERGELIJKING (ms) ---"
        printf "%-20s %8s %8s %8s %8s\n" "Scenario" "Gem" "P50" "P95" "P99"
        printf "%-20s %8s %8s %8s %8s\n" "--------" "---" "---" "---" "---"
        printf "%-20s %8s %8s %8s %8s\n" \
            "Baseline"  "${BASELINE_AVG:-N/A}"   "${BASELINE_P50:-N/A}"   "${BASELINE_P95:-N/A}"   "${BASELINE_P99:-N/A}"
        printf "%-20s %8s %8s %8s %8s\n" \
            "Met monitoring" "${MONITORED_AVG:-N/A}" "${MONITORED_P50:-N/A}" "${MONITORED_P95:-N/A}" "${MONITORED_P99:-N/A}"
        printf "%-20s %8s %8s %8s %8s\n" \
            "Crash scenario" "${CRASH_AVG:-N/A}"  "${CRASH_P50:-N/A}"  "${CRASH_P95:-N/A}"  "${CRASH_P99:-N/A}"
        echo ""
        echo "--- QUERY LATENCY (ms) ---"
        printf "  %-30s %6s\n" "Prometheus"     "${QUERY_PROMETHEUS_MS:-N/A}"
        printf "  %-30s %6s\n" "Loki"           "${QUERY_LOKI_MS:-N/A}"
        printf "  %-30s %6s\n" "Tempo"          "${QUERY_TEMPO_MS:-N/A}"
        printf "  %-30s %6s\n" "OpenObserve"    "${QUERY_OPENOBSERVE_MS:-N/A}"
        echo ""
        echo "--- OVERHEAD ---"
        if [ -n "$BASELINE_AVG" ] && [ -n "$MONITORED_AVG" ]; then
            local overhead=$(echo "scale=1; ($MONITORED_AVG - $BASELINE_AVG)" | bc 2>/dev/null || echo "?")
            local pct=$(echo "scale=1; ($MONITORED_AVG - $BASELINE_AVG) * 100 / $BASELINE_AVG" | bc 2>/dev/null || echo "?")
            echo "  Monitoring overhead: +${overhead}ms (+${pct}%)"
        fi
        echo ""
        echo "========================================================"
    } | tee "$REPORT_FILE"

    echo ""
    echo -e "${GREEN}Rapport opgeslagen in: $REPORT_FILE${NC}"
    echo -e "${GREEN}Raw data in: $RESULTS_FILE${NC}"
}

# ================================================================
# Main
# ================================================================
print_header
rm -f "$RESULTS_FILE"

check_stacks
test_baseline
test_with_monitoring
test_crash_scenario
test_query_performance
generate_report

echo ""
echo -e "${GREEN}Bekijk $REPORT_FILE voor het volledige rapport.${NC}"
