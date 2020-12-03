package tv.hd3g.jobkit.engine;

import static java.lang.Thread.State.RUNNABLE;
import static java.lang.Thread.State.TIMED_WAITING;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import java.util.Random;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class SpoolExecutorTest {

	static Random random = new Random();

	@Mock
	private ExecutionEvent event;

	String name;
	String spoolExecutorName;
	String threadName;
	long threadId;
	ThreadFactory threadFactory;
	SpoolExecutor spoolExecutor;

	@BeforeEach
	void init() throws Exception {
		MockitoAnnotations.openMocks(this).close();
		name = "InternalTest " + String.valueOf(System.nanoTime());

		spoolExecutorName = "Internal test Spool executor";
		threadName = "TestSpoolExecutor" + String.valueOf(System.nanoTime());
		threadFactory = r -> {
			final Thread t = new Thread(r);
			t.setDaemon(false);
			t.setName(threadName);
			threadId = t.getId();
			return t;
		};
		spoolExecutor = new SpoolExecutor(spoolExecutorName, event, threadFactory);
	}

	@Test
	void testAddToQueue() throws InterruptedException {
		final CountDownLatch smChkVerifyEvent = new CountDownLatch(1);
		final CountDownLatch smCmd = new CountDownLatch(1);
		final CountDownLatch smAfter = new CountDownLatch(1);

		assertTrue(spoolExecutor.addToQueue(() -> {
			try {
				smChkVerifyEvent.await(100, MILLISECONDS);
			} catch (final InterruptedException e1) {
				throw new IllegalStateException(e1);
			}
			smCmd.countDown();
		}, name, 0, e -> {
			smAfter.countDown();
		}));

		Thread.sleep(2);// NOSONAR
		verify(event, times(1)).beforeStart(
		        eq(name),
		        longThat(m -> m < System.currentTimeMillis()),
		        eq(spoolExecutor));
		verifyTotalAfterRunCorrectly(0);
		verifyTotalAfterFailedRun(0);
		smChkVerifyEvent.countDown();

		assertTrue(smCmd.await(100, MILLISECONDS));

		Thread.sleep(2);// NOSONAR
		verify(event, times(1)).afterRunCorrectly(eq(name),
		        longThat(m -> m < System.currentTimeMillis()),
		        longThat(m -> m < 100l),
		        eq(spoolExecutor));
		verifyTotalAfterFailedRun(0);

		assertTrue(smAfter.await(100, MILLISECONDS));

		verifyTotalBeforeStart(1);
		verifyTotalAfterRunCorrectly(1);
		verifyTotalAfterFailedRun(0);
		verify(event, times(0)).shutdownSpooler();
	}

	@Test
	void testAddToQueue_withError() throws InterruptedException {
		final int total = 5;

		for (int pos = 0; pos < total; pos++) {
			final CountDownLatch smAfter = new CountDownLatch(1);
			final AtomicReference<Exception> captured = new AtomicReference<>();
			final int _pos = pos;
			assertTrue(spoolExecutor.addToQueue(() -> {
				throw new IllegalArgumentException(String.valueOf(_pos));
			}, name, 0, e -> {
				captured.set(e);
				smAfter.countDown();
			}));
			assertTrue(smAfter.await(100, MILLISECONDS));
			assertNotNull(captured.get());
			assertEquals(String.valueOf(_pos), captured.get().getMessage());
		}

		Thread.sleep(2);// NOSONAR
		verify(event, times(total)).beforeStart(
		        eq(name),
		        longThat(m -> m < System.currentTimeMillis()),
		        eq(spoolExecutor));
		verifyTotalAfterRunCorrectly(0);
		verifyTotalAfterFailedRun(total);
		verify(event, times(total)).afterFailedRun(
		        eq(name),
		        longThat(m -> m < System.currentTimeMillis()),
		        longThat(m -> m < 100l),
		        eq(spoolExecutor),
		        any(IllegalArgumentException.class));

		verify(event, times(0)).shutdownSpooler();
	}

	@Test
	void testAddToQueue_multiple() throws InterruptedException {
		final int total = 50;

		final CountDownLatch smAfter = new CountDownLatch(total);
		final AtomicInteger count = new AtomicInteger(0);

		for (int pos = 0; pos < total; pos++) {
			assertTrue(spoolExecutor.addToQueue(() -> {
				count.incrementAndGet();
			}, name, 0, e -> {
				smAfter.countDown();
			}));
		}
		assertTrue(smAfter.await(500, MILLISECONDS));
		assertEquals(total, count.get());
	}

	@Test
	void testAddToQueue_onebyone() throws InterruptedException {
		final int total = 10;
		final AtomicInteger count = new AtomicInteger(0);

		for (int pos = 0; pos < total; pos++) {
			final CountDownLatch smAfter = new CountDownLatch(1);
			assertTrue(spoolExecutor.addToQueue(() -> {
				count.incrementAndGet();
			}, name, 0, e -> {
				smAfter.countDown();
			}));
			assertTrue(smAfter.await(100, MILLISECONDS));
		}
		assertEquals(total, count.get());
	}

	@Test
	void testAddToQueue_afterRunError() throws InterruptedException {
		final CountDownLatch smCmd0 = new CountDownLatch(1);

		assertTrue(spoolExecutor.addToQueue(() -> {
			smCmd0.countDown();
		}, name, 0, e -> {
			throw new IllegalArgumentException("This is a test error, this is normal if you see it in log message...");
		}));
		assertTrue(smCmd0.await(100, MILLISECONDS));

		final CountDownLatch smCmd1 = new CountDownLatch(2);
		assertTrue(spoolExecutor.addToQueue(() -> {
			smCmd1.countDown();
		}, name, 0, e -> {
			smCmd1.countDown();
		}));
		assertTrue(smCmd1.await(100, MILLISECONDS));
	}

	@Test
	void testGetQueueSize() throws InterruptedException {
		assertEquals(0, spoolExecutor.getQueueSize());

		final CountDownLatch smCmd0 = new CountDownLatch(1);
		final CountDownLatch smCmd1 = new CountDownLatch(1);
		final CountDownLatch reverseCmd = new CountDownLatch(1);
		spoolExecutor.addToQueue(() -> {
			smCmd0.countDown();
			try {
				reverseCmd.await(500, MILLISECONDS);
			} catch (final InterruptedException e1) {
				throw new IllegalStateException(e1);
			}
		}, name, 0, e -> {
			smCmd1.countDown();
		});

		smCmd0.await(100, MILLISECONDS);
		spoolExecutor.addToQueue(() -> {
		}, name, 0, e -> {
		});
		assertEquals(1, spoolExecutor.getQueueSize());
		reverseCmd.countDown();

		smCmd1.await(100, MILLISECONDS);
		Thread.sleep(50);// NOSONAR
		assertEquals(0, spoolExecutor.getQueueSize());

		verify(event, times(0)).shutdownSpooler();
	}

	@Test
	void testIsRunning() throws InterruptedException {
		assertFalse(spoolExecutor.isRunning());

		final CountDownLatch smCmd0 = new CountDownLatch(1);
		final CountDownLatch smCmd1 = new CountDownLatch(1);
		final CountDownLatch reverseCmd = new CountDownLatch(1);
		spoolExecutor.addToQueue(() -> {
			smCmd0.countDown();
			try {
				reverseCmd.await(500, MILLISECONDS);
			} catch (final InterruptedException e1) {
				throw new IllegalStateException(e1);
			}
		}, name, 0, e -> {
			smCmd1.countDown();
		});
		smCmd0.await(100, MILLISECONDS);
		assertTrue(spoolExecutor.isRunning());
		reverseCmd.countDown();
		smCmd1.await(100, MILLISECONDS);
		Thread.sleep(50);// NOSONAR
		assertFalse(spoolExecutor.isRunning());
	}

	@Test
	void testShutdown() throws InterruptedException {
		spoolExecutor.shutdown();

		final CountDownLatch smCmd = new CountDownLatch(1);
		assertFalse(spoolExecutor.addToQueue(() -> {
			smCmd.countDown();
		}, name, 0, e -> {
		}));
		assertFalse(smCmd.await(100, MILLISECONDS));

		verify(event, times(0)).shutdownSpooler();
	}

	@Test
	void testWaitToClose() {
		final AtomicInteger count = new AtomicInteger(0);

		spoolExecutor.addToQueue(() -> {
			try {
				Thread.sleep(100);// NOSONAR
			} catch (final InterruptedException e1) {
				throw new IllegalStateException(e1);
			}
			count.incrementAndGet();
		}, name, 0, e -> {
			count.incrementAndGet();
		});

		assertEquals(0, count.get());
		spoolExecutor.waitToClose();
		assertEquals(2, count.get());

		verify(event, times(0)).shutdownSpooler();
	}

	@Test
	void testGetLastStatus() throws InterruptedException {
		/**
		 * No one run
		 */
		var lastStatus = spoolExecutor.getLastStatus();
		assertNotNull(lastStatus);
		assertEquals(spoolExecutorName, lastStatus.getSpoolName());
		assertNull(lastStatus.getCurrentOperationName());
		assertNull(lastStatus.getCurrentThreadState());
		assertNull(lastStatus.getCurrentThreadName());
		assertEquals(-1l, lastStatus.getCurrentThreadId());
		assertNotNull(lastStatus.getQueue());
		assertEquals(0, lastStatus.getQueue().size());
		assertFalse(lastStatus.isShutdown());

		final CountDownLatch lockJob0Run = new CountDownLatch(1);
		final CountDownLatch lockJob0AfterRun = new CountDownLatch(1);
		final CountDownLatch lockJob1Run = new CountDownLatch(1);
		spoolExecutor.addToQueue(() -> {
			try {
				lockJob0Run.await(100, MILLISECONDS);
			} catch (final InterruptedException e1) {
				throw new IllegalStateException(e1);
			}
		}, name, 0, e -> {
			try {
				lockJob0AfterRun.await(100, MILLISECONDS);
			} catch (final InterruptedException e1) {
				throw new IllegalStateException(e1);
			}
		});

		spoolExecutor.addToQueue(() -> {
			try {
				lockJob1Run.await(500, MILLISECONDS);
			} catch (final InterruptedException e1) {
				throw new IllegalStateException(e1);
			}
		}, name + "-2", 0, e -> {
		});

		/**
		 * During the first run
		 */
		lastStatus = spoolExecutor.getLastStatus();
		assertNotNull(lastStatus);
		assertEquals(spoolExecutorName, lastStatus.getSpoolName());
		assertEquals(name, lastStatus.getCurrentOperationName());
		assertTrue(lastStatus.getCurrentThreadState() == RUNNABLE
		           || lastStatus.getCurrentThreadState() == TIMED_WAITING);
		assertEquals(threadName, lastStatus.getCurrentThreadName());
		assertEquals(threadId, lastStatus.getCurrentThreadId());
		assertEquals(1, lastStatus.getQueue().size());
		assertFalse(lastStatus.isShutdown());

		lockJob0Run.countDown();
		Thread.sleep(50);// NOSONAR

		/**
		 * After the first run
		 */
		lastStatus = spoolExecutor.getLastStatus();
		assertNotNull(lastStatus);
		assertEquals(spoolExecutorName, lastStatus.getSpoolName());
		assertEquals(name, lastStatus.getCurrentOperationName());
		assertEquals(TIMED_WAITING, lastStatus.getCurrentThreadState());
		assertEquals(threadName, lastStatus.getCurrentThreadName());
		assertEquals(threadId, lastStatus.getCurrentThreadId());
		assertEquals(1, lastStatus.getQueue().size());
		assertFalse(lastStatus.isShutdown());

		lockJob0AfterRun.countDown();
		Thread.sleep(50);// NOSONAR

		/**
		 * During the second run
		 */
		lastStatus = spoolExecutor.getLastStatus();
		assertEquals(0, lastStatus.getQueue().size());

		lockJob1Run.countDown();
		Thread.sleep(50);// NOSONAR

		/**
		 * All done
		 */
		lastStatus = spoolExecutor.getLastStatus();
		assertNotNull(lastStatus);
		assertEquals(spoolExecutorName, lastStatus.getSpoolName());
		assertNull(lastStatus.getCurrentOperationName());
		assertNull(lastStatus.getCurrentThreadState());
		assertNull(lastStatus.getCurrentThreadName());
		assertEquals(-1l, lastStatus.getCurrentThreadId());
		assertEquals(0, lastStatus.getQueue().size());
		assertFalse(lastStatus.isShutdown());

		spoolExecutor.shutdown();
		lastStatus = spoolExecutor.getLastStatus();
		assertTrue(lastStatus.isShutdown());
	}

	@Test
	void testPriorities() throws InterruptedException {
		final long startDate = System.currentTimeMillis();
		final int count = 10;
		final long sleep = 10;

		final CountDownLatch latch = new CountDownLatch(count);

		class PJob {
			volatile long startTime;
			final Runnable command = () -> {
				startTime = System.currentTimeMillis() - startDate;
				try {
					Thread.sleep(sleep);// NOSONAR
				} catch (final InterruptedException e) {// NOSONAR
				}
				latch.countDown();
			};
			final String name = "N" /*+ String.valueOf(random.nextInt(10000))*/;
			final int priority = random.nextInt(count * 100);
			final Consumer<Exception> afterRunCommand = e -> {
			};
		}

		final var allPjobs = IntStream.range(0, count)
		        .mapToObj(i -> new PJob())
		        .collect(Collectors.toUnmodifiableList());
		allPjobs.forEach(j -> spoolExecutor.addToQueue(j.command, j.name, j.priority, j.afterRunCommand));

		latch.await(count * sleep * 2, MILLISECONDS);

		/**
		 * skip 1 because the first pushed will be always random.
		 * During the first sleep, the others will be added, and correcly sorted.
		 */
		final var dateSort = allPjobs.stream()
		        .skip(1)
		        .sorted((l, r) -> Long.compare(l.startTime, r.startTime))
		        .map(j -> j.name + "_t" + j.startTime + "_P" + j.priority)
		        .collect(Collectors.toUnmodifiableList());

		final var prioSort = allPjobs.stream()
		        .skip(1)
		        .sorted((l, r) -> Integer.compare(r.priority, l.priority))
		        .map(j -> j.name + "_t" + j.startTime + "_P" + j.priority)
		        .collect(Collectors.toUnmodifiableList());

		assertEquals(prioSort, dateSort);
	}

	private void verifyTotalBeforeStart(final int count) {
		verify(event, times(count)).beforeStart(any(String.class),
		        any(long.class), any(SpoolExecutor.class));
	}

	private void verifyTotalAfterRunCorrectly(final int count) {
		verify(event, times(count)).afterRunCorrectly(any(String.class),
		        any(long.class), any(long.class), any(SpoolExecutor.class));
	}

	private void verifyTotalAfterFailedRun(final int count) {
		verify(event, times(count)).afterFailedRun(any(String.class), any(long.class), any(long.class),
		        any(SpoolExecutor.class), any(Exception.class));
	}

}
