package tv.hd3g.jobkit.engine;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import tv.hd3g.jobkit.engine.status.BackgroundServiceStatus;

class JobKitEngineTest {

	static ScheduledExecutorService scheduledExecutor = Executors.newScheduledThreadPool(1);
	static Random random = new Random();

	@Mock
	ExecutionEvent executionEvent;
	@Mock
	BackgroundServiceEvent backgroundServiceEvent;
	@Mock
	Runnable task;
	@Mock
	Consumer<Exception> afterRunCommand;

	String name;
	String spoolName;
	JobKitEngine jobKitEngine;
	Spooler spooler;

	@BeforeEach
	void init() throws Exception {
		MockitoAnnotations.openMocks(this).close();
		name = String.valueOf(random.nextLong());
		spoolName = String.valueOf(random.nextLong());
		jobKitEngine = new JobKitEngine(scheduledExecutor, executionEvent, backgroundServiceEvent);
		spooler = jobKitEngine.getSpooler();
	}

	@AfterEach
	void close() {
		jobKitEngine.waitToClose();
	}

	@AfterAll
	static void end() {
		scheduledExecutor.shutdownNow();
	}

	@Test
	void testRunOneShot() {
		assertTrue(jobKitEngine.runOneShot(name, spoolName, 0, task, afterRunCommand));

		while (spooler.getRunningQueuesCount() > 0) {
			Thread.onSpinWait();
		}

		verify(task, times(1)).run();
		verify(afterRunCommand, times(1)).accept(isNull());
	}

	@Test
	void testCreateService() {
		final var s = jobKitEngine.createService(name, spoolName, task);
		assertNotNull(s);
		assertFalse(s.isEnabled());
		assertEquals(0, s.getPriority());
		assertEquals(0, s.getTimedInterval(TimeUnit.MILLISECONDS));
	}

	@Test
	void testStartServiceStringStringLongTimeUnitRunnable() throws Exception {
		final var i = new AtomicInteger();
		task = () -> i.getAndIncrement();

		final var s = jobKitEngine.startService(name, spoolName, 1, TimeUnit.MILLISECONDS, task);
		assertNotNull(s);
		assertTrue(s.isEnabled());
		assertEquals(0, s.getPriority());
		assertEquals(1, s.getTimedInterval(TimeUnit.MILLISECONDS));

		CompletableFuture.runAsync(() -> {
			while (i.get() == 0) {
				Thread.onSpinWait();
			}
		}).get(1, TimeUnit.SECONDS);
	}

	@Test
	void testStartServiceStringStringDurationRunnable() throws Exception {
		final var i = new AtomicInteger();
		task = () -> i.getAndIncrement();

		final var s = jobKitEngine.startService(name, spoolName, Duration.ofMillis(1), task);
		assertNotNull(s);
		assertTrue(s.isEnabled());
		assertEquals(0, s.getPriority());
		assertEquals(1, s.getTimedInterval(TimeUnit.MILLISECONDS));

		CompletableFuture.runAsync(() -> {
			while (i.get() == 0) {
				Thread.onSpinWait();
			}
		}).get(1, TimeUnit.SECONDS);
	}

	@Test
	void testGetSpooler() {
		assertNotNull(jobKitEngine.getSpooler());
	}

	@Test
	void testShutdown() {
		jobKitEngine.startService(name, spoolName, 1, TimeUnit.DAYS, task);
		jobKitEngine.shutdown();
		assertTrue(jobKitEngine.getLastStatus().getSpoolerStatus().isShutdown());
		assertTrue(jobKitEngine.getLastStatus().getBackgroundServicesStatus().stream()
		        .noneMatch(BackgroundServiceStatus::isEnabled));
	}

	@Test
	void testWaitToClose() {
		jobKitEngine.startService(name, spoolName, 1, TimeUnit.DAYS, task);
		jobKitEngine.waitToClose();
		assertTrue(jobKitEngine.getLastStatus().getSpoolerStatus().isShutdown());
		assertTrue(jobKitEngine.getLastStatus().getBackgroundServicesStatus().stream()
		        .noneMatch(BackgroundServiceStatus::isEnabled));
	}

	@Test
	void testGetLastStatus() {
		final var status = jobKitEngine.getLastStatus();
		assertNotNull(status);
		assertNotNull(status.getSpoolerStatus());
		assertEquals(0, status.getSpoolerStatus().getCreatedThreadsCount());
		assertNotNull(status.getBackgroundServicesStatus());
		assertEquals(0, status.getBackgroundServicesStatus().size());
	}

}
