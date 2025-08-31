package io.df.amdahl

import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import java.time.Duration
import java.util.concurrent.Executors
import kotlin.math.max
import kotlin.system.measureTimeMillis

/**
 * 암달의 법칙(Amdahl’s Law)을 이커머스 주문 처리 파이프라인으로 시뮬레이션하는 데모 코드
 *
 * 도메인 가정:
 * - 20% 직렬 처리 (예: 결제 승인, 재고 락)
 * - 80% 병렬 처리 가능 (예: 세금/배송비/쿠폰 계산, 데이터 보강)
 *
 * 시뮬레이션 흐름:
 * - 주문 1건은 다음 두 단계를 거친다:
 *   1) serialPhase(): 직렬 처리 단계 (병렬 불가, 20%)
 *   2) parallelPhase(): 병렬 처리 단계 (병렬 가능, 80%)
 *
 * 비교를 위해 단일 워커에서 주문 하나 처리 시간을 10ms (직렬 2ms + 병렬 8ms)로 설정.
 * 병렬 워커 개수 N을 바꿔가며 총 처리 시간과 가속비를 측정한다.
 *
 * 이론적 가속비:
 *   S(N) = 1 / ((1 - P) + (P / N))   (P = 0.8)
 *
 * 참고:
 * - 실제 시스템은 스케줄링/GC/IO 등으로 오버헤드가 발생한다.
 * - 직렬/병렬 분리는 주문 단위로 이루어지며, 각 주문의 직렬 처리 후 병렬 계산이 시작된다.
 * - 여러 주문은 병렬 단계에서 동시에 실행될 수 있으나, N 워커 수를 초과할 수 없다.
 */
object AmdahlEcommerceDemo {

    /** 주문 품목 */
    data class Item(val id: String, val price: Long, val qty: Int)

    /** 주문 */
    data class Order(val id: String, val items: List<Item>)

    // ----- 시뮬레이션 파라미터 -----
    private const val SERIAL_MS = 2L       // 직렬 처리 지연시간 (ms), 전체의 약 20%
    private const val PARALLEL_MS = 8L     // 병렬 처리 지연시간 (ms), 전체의 약 80%
    private const val ORDERS = 5_000       // 시뮬레이션할 주문 개수
    private val WORKERS = listOf(1, 2, 4, 8, 16, 32) // 테스트할 병렬 워커 수
    private const val P = 0.8              // 병렬화 가능 비율

    /**
     * 더미 주문 생성기
     * - 주문 ID와 상품 SKU를 자동 생성
     */
    private fun makeOrders(n: Int): List<Order> =
        (1..n).map { idx ->
            val items = listOf(
                Item("SKU-${idx}-A", price = 1200, qty = 1),
                Item("SKU-${idx}-B", price = 3500, qty = 2),
                Item("SKU-${idx}-C", price = 990, qty = 1),
            )
            Order("ORDER-$idx", items)
        }

    /**
     * 직렬 처리 단계
     * - 병렬 불가, 모든 주문이 순차적으로 처리됨
     * - 예: 결제 승인, 재고 잠금, 사기 탐지 1차 확인
     */
    private suspend fun serialPhase(order: Order) {
        delay(SERIAL_MS) // 직렬 단계 지연 시뮬레이션
        if (order.items.isEmpty()) error("주문 품목이 없음 (비정상 상태)") // 최적화 방지용 체크
    }

    /**
     * 병렬 처리 단계
     * - 병렬 가능, 여러 주문이 동시에 처리 가능
     * - 예: 가격 계산, 쿠폰/할인 적용, 세금/배송비 계산
     */
    private suspend fun parallelPhase(order: Order) {
        // 병렬 구간이 진짜로 워커 스레드를 붙잡도록 만든다.
        Thread.sleep(PARALLEL_MS)

        // 실제 계산이 있는 것처럼 간단한 수식 처리
        var total = 0L
        for (i in order.items) {
            total += i.price * i.qty
        }
        val discounted = (total * 95) / 100         // 5% 쿠폰
        val taxed = (discounted * 110) / 100        // 10% 부가세
        val shipping = max(0, 3000 - (taxed / 100)) // 단순 배송비 규칙
        check(taxed + shipping >= 0) { "계산 결과 오류 방지" }
    }

    /**
     * 주문 파이프라인 실행기
     *
     * @param orders 처리할 주문 목록
     * @param parallelWorkers 병렬 워커 수
     * @return 전체 처리 시간 (ms)
     *
     * 동작:
     * - 직렬 단계: 세마포어(permit=1)로 보호 → 동시에 하나만 실행됨
     * - 병렬 단계: 고정 크기 스레드 풀(워커 N개)에 분배 → 동시에 최대 N개 실행
     */
    private suspend fun runPipeline(orders: List<Order>, parallelWorkers: Int): Long {
        val serialGate = Semaphore(1) // 직렬 구간 보호용 (동시에 하나만 실행)

        val executor = Executors.newFixedThreadPool(parallelWorkers.coerceAtLeast(1))
        val dispatcher = executor.asCoroutineDispatcher()

        try {
            val timeMs = measureTimeMillis {
                coroutineScope {
                    for (o in orders) {
                        launch {
                            // 직렬 단계: 세마포어 lock
                            serialGate.withPermit {
                                serialPhase(o)
                            }
                            // 병렬 단계: 병렬 워커 풀에 배치
                            withContext(dispatcher) {
                                parallelPhase(o)
                            }
                        }
                    }
                }
            }
            return timeMs
        } finally {
            dispatcher.close()
            executor.shutdown()
        }
    }

    /** 이론적 가속비 계산 함수 */
    private fun theoreticalSpeedup(n: Int, p: Double = P): Double {
        val denom = (1.0 - p) + (p / n.toDouble())
        return 1.0 / denom
    }

    /** ms → "Xs Yms" 문자열 변환 */
    private fun prettyDuration(ms: Long): String {
        val d = Duration.ofMillis(ms)
        val sec = d.seconds
        val millis = d.toMillisPart()
        return "${sec}s ${millis}ms"
    }

    /**
     * 실행 시작점 (main 함수)
     * - 이론 가속비 표 출력
     * - N=1 baseline 실행 후, 워커 수를 늘려가며 측정치 vs 이론 비교
     */
    @JvmStatic
    fun main(args: Array<String>) = runBlocking {
        println("=== 암달의 법칙 데모 (이커머스 주문) ===")
        println("가정: 직렬 20%, 병렬 80%; 단일 워커 기준 주문 1건 ≈ ${SERIAL_MS + PARALLEL_MS}ms")
        println("주문 수: $ORDERS\n")

        // 이론적 가속비 출력
        println("이론적 가속비 (P=0.8)")
        println("N\tS(N)")
        for (n in WORKERS) {
            println("$n\t${"%.3f".format(theoreticalSpeedup(n))}")
        }
        println()

        val orders = makeOrders(ORDERS)

        // baseline (N=1) 측정
        val baselineMs = runPipeline(orders, parallelWorkers = 1)
        println("Baseline (N=1) 전체 시간: ${prettyDuration(baselineMs)}  [${baselineMs}ms]\n")

        // 실제 측정 vs 이론 비교
        println("실제 측정 결과 vs 이론 비교:")
        println("N\t실측 시간\t실측 가속비\t\t이론 가속비")
        for (n in WORKERS) {
            val t = runPipeline(orders, parallelWorkers = n)
            val measured = baselineMs.toDouble() / t.toDouble()
            val theory = theoreticalSpeedup(n)
            println("$n\t${prettyDuration(t)}\t${"%.3f".format(measured)}\t\t${"%.3f".format(theory)}")
        }

        println("\n참고사항:")
        println("- 이론과 차이가 나는 것은 스케줄링, GC, 오버헤드 때문임.")
        println("- ORDERS나 PARALLEL_MS를 크게 하면 이론치에 더 근접함.")
        println("- 실제 시스템은 DB/캐시/락 등의 추가 병목이 직렬 비율을 더 키움.")
    }
}
