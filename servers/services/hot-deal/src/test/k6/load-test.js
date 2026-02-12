import http from 'k6/http';
import { check, sleep } from 'k6';
import { Counter, Rate, Trend } from 'k6/metrics';

// ─── 설정 ─────────────────────────────────
const HOT_DEAL_ID = '280227607700733952';
const BASE_URL = 'http://localhost:8089';

// ─── 커스텀 메트릭 ──────────────────────────
const purchaseSuccess = new Counter('purchase_success');
const purchaseFail = new Counter('purchase_fail');
const queueEnterSuccess = new Counter('queue_enter_success');
const purchaseRate = new Rate('purchase_success_rate');
const purchaseDuration = new Trend('purchase_duration');

// ─── 시나리오 ────────────────────────────────
// 100명 동시 접속 → 대기열 진입 → 입장 대기 → 구매 시도
export const options = {
    scenarios: {
        rush: {
            executor: 'shared-iterations',
            vus: 100,
            iterations: 100,
            maxDuration: '60s',
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<2000'],
        purchase_success_rate: ['rate>0'],
    },
};

export default function () {
    const userId = __VU;
    const headers = {
        'Content-Type': 'application/json',
        'X-User-Id': String(userId),
    };

    // 1. 대기열 진입
    const enterRes = http.post(
        `${BASE_URL}/api/v1/hot-deals/${HOT_DEAL_ID}/queue/enter`,
        null,
        { headers }
    );

    const enterOk = check(enterRes, {
        'queue enter status 200': (r) => r.status === 200,
    });

    if (enterOk) {
        queueEnterSuccess.add(1);
    }

    // 2. 입장 대기 (폴링)
    let admitted = false;
    for (let i = 0; i < 30; i++) {
        sleep(1);

        const statusRes = http.get(
            `${BASE_URL}/api/v1/hot-deals/${HOT_DEAL_ID}/queue`,
            { headers }
        );

        if (statusRes.status === 200) {
            const body = JSON.parse(statusRes.body);
            if (body.data && body.data.canPurchase) {
                admitted = true;
                break;
            }
        }
    }

    if (!admitted) {
        purchaseFail.add(1);
        purchaseRate.add(false);
        return;
    }

    // 3. 구매 시도
    const start = Date.now();
    const purchaseRes = http.post(
        `${BASE_URL}/api/v1/hot-deals/${HOT_DEAL_ID}/purchase`,
        JSON.stringify({ quantity: 1 }),
        { headers }
    );
    purchaseDuration.add(Date.now() - start);

    const purchaseOk = check(purchaseRes, {
        'purchase status 200': (r) => r.status === 200,
    });

    if (purchaseOk) {
        const body = JSON.parse(purchaseRes.body);
        if (body.data && body.data.success) {
            purchaseSuccess.add(1);
            purchaseRate.add(true);
        } else {
            purchaseFail.add(1);
            purchaseRate.add(false);
        }
    } else {
        purchaseFail.add(1);
        purchaseRate.add(false);
    }
}

export function handleSummary(data) {
    const success = data.metrics.purchase_success ? data.metrics.purchase_success.values.count : 0;
    const fail = data.metrics.purchase_fail ? data.metrics.purchase_fail.values.count : 0;
    const total = success + fail;

    console.log('\n=== 부하 테스트 결과 ===');
    console.log(`총 시도: ${total}`);
    console.log(`구매 성공: ${success}`);
    console.log(`구매 실패: ${fail} (재고 소진 포함)`);
    console.log(`성공률: ${total > 0 ? ((success / total) * 100).toFixed(1) : 0}%`);

    if (data.metrics.purchase_duration) {
        const dur = data.metrics.purchase_duration.values;
        console.log(`구매 응답시간 p50: ${dur.med?.toFixed(0)}ms, p95: ${dur['p(95)']?.toFixed(0)}ms`);
    }

    return {};
}
