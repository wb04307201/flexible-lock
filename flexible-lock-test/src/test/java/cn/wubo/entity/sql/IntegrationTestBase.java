package cn.wubo.entity.sql;

import org.junit.jupiter.api.Assumptions;

/**
 * Common base for tests that need a live Redis or ZooKeeper.
 *
 * <p>Each subclass declares which services it requires by overriding
 * {@link #requiresRedis()} and/or {@link #requiresZooKeeper()}. Before any
 * test runs, {@code beforeAll} pings the service and calls
 * {@link Assumptions#assumeTrue(boolean, String)} so that:
 * <ul>
 *   <li>CI without Docker installed reports <strong>skipped</strong> instead
 *       of hanging on connection timeouts (which can be 30s+ per test);</li>
 *   <li>Developers can run {@code mvn test} locally and the same suite
 *       adapts to whatever services are available.</li>
 * </ul>
 *
 * <p>The check uses a 1-second TCP connect timeout so a missing service
 * is detected in well under a second.
 */
public abstract class IntegrationTestBase {

    private static final int CONNECT_TIMEOUT_MS = 1000;

    static void beforeAll() {
        // No-op: subclasses may override; we just keep the hook for symmetry.
    }

    /** Override to declare that this test class needs Redis on 127.0.0.1:6379. */
    protected boolean requiresRedis() {
        return false;
    }

    /** Override to declare that this test class needs a Redis cluster on 127.0.0.1:7000. */
    protected boolean requiresRedisCluster() {
        return false;
    }

    /** Override to declare that this test class needs Redis Sentinel on 127.0.0.1:26379. */
    protected boolean requiresRedisSentinel() {
        return false;
    }

    /** Override to declare that this test class needs ZooKeeper on 127.0.0.1:2181. */
    protected boolean requiresZooKeeper() {
        return false;
    }

    /** JUnit 5 calls this for every test method; the Assumptions are evaluated
     *  on the first test (services are static) and short-circuit after that. */
    @org.junit.jupiter.api.BeforeEach
    void checkServices() {
        if (requiresRedis()) {
            Assumptions.assumeTrue(isReachable("127.0.0.1", 6379),
                    "Redis is not reachable on 127.0.0.1:6379 — start it with: "
                            + "docker run -d --name flexible-lock-redis -p 6379:6379 redis:6-alpine");
        }
        if (requiresRedisCluster()) {
            Assumptions.assumeTrue(isReachable("127.0.0.1", 7000),
                    "Redis cluster is not reachable on 127.0.0.1:7000 — see RedisClusterLockTest Javadoc for setup");
        }
        if (requiresRedisSentinel()) {
            Assumptions.assumeTrue(isReachable("127.0.0.1", 26379),
                    "Redis sentinel is not reachable on 127.0.0.1:26379 — see RedisSentinelLockTest Javadoc for setup");
        }
        if (requiresZooKeeper()) {
            Assumptions.assumeTrue(isReachable("127.0.0.1", 2181),
                    "ZooKeeper is not reachable on 127.0.0.1:2181 — start it with: "
                            + "docker run -d --name flexible-lock-zk -p 2181:2181 zookeeper:latest");
        }
    }

    /** Detect `it.skip=true` to force-skip integration tests (e.g., in CI). */
    @org.junit.jupiter.api.BeforeEach
    void checkSkipFlag() {
        Assumptions.assumeTrue(!"true".equalsIgnoreCase(System.getProperty("it.skip")),
                "Integration tests skipped via -Dit.skip=true");
    }

    private static boolean isReachable(String host, int port) {
        try (java.net.Socket socket = new java.net.Socket()) {
            socket.connect(new java.net.InetSocketAddress(host, port), CONNECT_TIMEOUT_MS);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
