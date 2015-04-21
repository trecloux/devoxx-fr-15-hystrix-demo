package hystrix.demo;

import com.google.common.base.Stopwatch;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.JdkFutureAdapters;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.common.util.concurrent.Uninterruptibles;
import com.netflix.config.ConfigurationManager;
import com.netflix.hystrix.*;
import com.netflix.hystrix.exception.HystrixRuntimeException;
import com.netflix.hystrix.strategy.HystrixPlugins;
import com.netflix.hystrix.strategy.eventnotifier.HystrixEventNotifier;
import org.hamcrest.core.IsInstanceOf;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.*;

import static com.google.common.util.concurrent.JdkFutureAdapters.listenInPoolThread;
import static com.google.common.util.concurrent.Uninterruptibles.sleepUninterruptibly;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;
import static org.hamcrest.core.IsInstanceOf.instanceOf;
import static org.junit.internal.matchers.ThrowableCauseMatcher.hasCause;

public class HystrixTest {
    @Rule
    public ExpectedException expectedException = ExpectedException.none();
    HystrixCommandKey commandKey = HystrixCommandKey.Factory.asKey("MyCommand");

    @Test
    public void should_not_fail() throws Exception {
        MyCommand command = MyCommand.successful();
        assertThat(command.execute()).isEqualTo("OK");
    }

    @Test
    public void should_not_fail_async() throws Exception {
        MyCommand command = MyCommand.successful();
        Future<String> future = command.queue();
        assertThat(future.get()).isEqualTo("OK");
    }


    @Test
    public void should_timeout() throws Exception {
        expectedException.expect(HystrixRuntimeException.class);
        expectedException.expectCause(IsInstanceOf.instanceOf(TimeoutException.class));

        MyCommand command = MyCommand.withTimeout(2000);
        command.execute();
    }

    @Test
    public void should_propagate_exception() throws Exception {
        expectedException.expect(HystrixRuntimeException.class);
        expectedException.expectCause(IsInstanceOf.instanceOf(IllegalStateException.class));

        MyCommand command = MyCommand.withException();
        command.execute();
    }

    @Test
    public void should_fallback() throws Exception {
        MyCommand command = MyCommand.withTimeoutAndFallback(2000);
        assertThat(command.execute()).isEqualTo("KO");
    }

    @Test
    public void should_open_circuit_on_timeouts() throws Exception {
        triggerCommandsToOpenTheCircuit(() -> MyCommand.withTimeout(2000).execute());

        assertCircuitIsOpen();
        assertCommandIsShortCircuited();
    }

    @Test
    public void should_open_circuit_on_exceptions() throws Exception {
        Uninterruptibles.sleepUninterruptibly(500, MILLISECONDS);
        assertThat(MyCommand.successful().execute()).isEqualTo("OK");

        triggerCommandsToOpenTheCircuit(() -> MyCommand.withException().execute());

        assertCircuitIsOpen();
        assertCommandIsShortCircuited();
    }

    @Test
    public void should_close_circuit() throws Exception {
        triggerCommandsToOpenTheCircuit(() -> MyCommand.withTimeout(2000).execute());

        assertCircuitIsOpen();
        Uninterruptibles.sleepUninterruptibly(10, SECONDS);

        String result = MyCommand.successful().execute();
        assertThat(result).isEqualTo("OK");
        assertCircuitIsClosed();
    }

    private void assertCommandIsShortCircuited() {
        Stopwatch stopwatch = Stopwatch.createStarted();
        String result = MyCommand.withTimeoutAndFallback(2000).execute();
        assertThat(result).isEqualTo("KO");
        assertThat(stopwatch.stop().elapsed(MILLISECONDS)).isLessThan(10);
    }

    private void assertCircuitIsOpen() {
        HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(commandKey);
        assertThat(circuitBreaker.isOpen()).isTrue();
    }

    private void assertCircuitIsClosed() {
        HystrixCircuitBreaker circuitBreaker = HystrixCircuitBreaker.Factory.getInstance(commandKey);
        assertThat(circuitBreaker.isOpen()).isFalse();
    }

    private void triggerCommandsToOpenTheCircuit(Callable<String> callable) {
        ExecutorService executor = Executors.newFixedThreadPool(10);
        Future<String> future = null;
        for (int i = 0; i < 30; i++) {
            future = executor.submit(callable);
        }
        // Wait for the last failure
        try {
            future.get();
            fail("Should have throw an exception");
        } catch (Exception e) {
        }
    }

    private static class MyCommand extends HystrixCommand<String> {
        private long timeout;
        private boolean fallback;
        private boolean exception;

        private MyCommand(long timeout, boolean fallback, boolean exception) {
            super(HystrixCommandGroupKey.Factory.asKey("MyGroup"));
            this.timeout = timeout;
            this.fallback = fallback;
            this.exception = exception;
        }

        public static MyCommand successful() {
            return withTimeout(0);
        }
        public static MyCommand withTimeout(long timeout) {
            return new MyCommand(timeout, false, false);
        }
        public static MyCommand withTimeoutAndFallback(long timeout) {
            return new MyCommand(timeout, true, false);
        }
        public static MyCommand withException() {
            return new MyCommand(0, false, true);
        }

        @Override
        protected String run() throws Exception {
            if (exception) {
                throw new IllegalStateException();
            }
            // should be a network call
            Uninterruptibles.sleepUninterruptibly(timeout, MILLISECONDS);
            return "OK";
        }
        @Override
        protected String getFallback() {
            if (fallback) {
                return "KO";
            } else {
                return super.getFallback();
            }
        }


    }

    @Before
    public void setUp() throws Exception {
        Hystrix.reset();

        HystrixPlugins.getInstance().registerEventNotifier(new HystrixEventNotifier() {

            Logger logger = LoggerFactory.getLogger(this.getClass());

            @Override
            public void markCommandExecution(HystrixCommandKey key, HystrixCommandProperties.ExecutionIsolationStrategy isolationStrategy, int duration, List<HystrixEventType> eventsDuringExecution) {
                super.markCommandExecution(key, isolationStrategy, duration, eventsDuringExecution);
                logger.debug("{}, {}, {}", key.name(), duration, eventsDuringExecution);
            }

            @Override
            public void markEvent(HystrixEventType eventType, HystrixCommandKey key) {
                super.markEvent(eventType, key);
                logger.debug("{}, {}", eventType, key.name());

            }
        });

        // Specific configuration to easily simulate open/close behavior
        ConfigurationManager.getConfigInstance().addProperty("hystrix.threadpool.default.maxQueueSize", 30);
        ConfigurationManager.getConfigInstance().addProperty("hystrix.threadpool.default.queueSizeRejectionThreshold", 30);
        ConfigurationManager.getConfigInstance().addProperty("hystrix.command.default.metrics.healthSnapshot.intervalInMilliseconds", 1);



    }
}
