package io.github.henriquevmdev.erpconnector.core.retry;

import java.time.Duration;

/** How {@link Retrier} waits. Injected so tests run without real sleeping. */
@FunctionalInterface
public interface Sleeper {

    Sleeper SYSTEM = Thread::sleep;

    void sleep(Duration duration) throws InterruptedException;
}
