package tv.hd3g.jobkit.engine;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.longThat;
import static org.mockito.Mockito.verify;
import static org.mockito.internal.verification.VerificationModeFactory.atLeast;
import static org.mockito.internal.verification.VerificationModeFactory.times;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.mockito.verification.VerificationMode;

class BackgroundServiceEventTest {

	@Mock
	BackgroundServiceEvent event;

	ScheduledExecutorService scheduledExecutorService;
	CountDownLatch latch;
	BackgroundService service;
	String name;
	String spoolName;
	Spooler spooler;
	AtomicReference<RuntimeException> error;
	long interval;

	@BeforeEach
	void init() {
		MockitoAnnotations.initMocks(this);

		interval = 10l;
		scheduledExecutorService = Executors.newScheduledThreadPool(1);
		latch = new CountDownLatch(1);
		name = String.valueOf(System.nanoTime());
		spoolName = String.valueOf(System.nanoTime());
		spooler = new Spooler(new ExecutionEvent() {});
		error = new AtomicReference<>();

		service = new BackgroundService(name, spoolName, spooler, scheduledExecutorService, event, () -> {
			try {
				latch.await(500, MILLISECONDS);
			} catch (final InterruptedException e1) {
				throw new IllegalStateException(e1);
			}
			final var e = error.get();
			if (e != null) {
				throw e;
			}
		});
		service.setTimedInterval(interval, TimeUnit.MILLISECONDS);
	}

	@AfterEach
	void close() {
		service.disable();
		spooler.shutdown();
		scheduledExecutorService.shutdown();
	}

	@Test
	void completeCycle_scheduleNextBackgroundServiceTask() throws InterruptedException {
		verifyScheduleNextBackgroundServiceTask(times(0));
		service.enable();

		verify(event, times(1)).scheduleNextBackgroundServiceTask(eq(name), eq(spoolName), eq(0), eq(interval));

		latch.countDown();
		verifyScheduleNextBackgroundServiceTask(times(1));

		Thread.sleep(10 * interval);// NOSONAR

		verifyScheduleNextBackgroundServiceTask(atLeast(2));
	}

	@Test
	void completeCycle_nextBackgroundServiceTask() throws InterruptedException {
		latch.countDown();
		service.enable();
		verifyNextBackgroundServiceTask(times(0));

		Thread.sleep(10 * interval);// NOSONAR

		verify(event, atLeast(1)).nextBackgroundServiceTask(eq(name), eq(spoolName), eq(0));
	}

	@Test
	void completeCycle_planNextExec() throws InterruptedException {
		service.enable();

		verifyPlanNextExec(times(0));
		latch.countDown();
		verifyPlanNextExec(times(0));

		Thread.sleep(10 * interval);// NOSONAR

		verify(event, atLeast(1)).planNextExec(eq(name), eq(spoolName), eq(interval));

		error.set(new RuntimeException("This is a test error"));
		Thread.sleep(10 * interval);// NOSONAR

		verify(event, atLeast(1)).planNextExec(eq(name), eq(spoolName), eq(interval));

		service.setRetryAfterTimeFactor(2);
		Thread.sleep(10 * interval);// NOSONAR

		verify(event, atLeast(1)).planNextExec(eq(name), eq(spoolName), longThat(m -> m > interval));
	}

	@Test
	void completeCycle_onPreviousRunWithError() throws InterruptedException {
		service.enable();
		latch.countDown();
		Thread.sleep(10 * interval);// NOSONAR

		verifyOnPreviousRunWithError(times(0));

		error.set(new RuntimeException("This is a test error"));
		Thread.sleep(10 * interval);// NOSONAR

		verify(event, atLeast(1)).onPreviousRunWithError(eq(name), eq(spoolName), eq(error.get()));
	}

	@Test
	void completeCycle_onChangeTimedInterval() throws InterruptedException {
		verifyOnChangeTimedInterval(times(1));
		service.enable();
		latch.countDown();
		verifyOnChangeTimedInterval(times(1));
		service.setTimedInterval(interval * 2, TimeUnit.MILLISECONDS);

		verify(event, times(1)).onChangeTimedInterval(eq(name), eq(spoolName), eq(interval * 2));

		service.disable();

		verifyOnChangeTimedInterval(times(2));

		service.setTimedInterval(interval * 3, TimeUnit.MILLISECONDS);

		verify(event, times(1)).onChangeTimedInterval(eq(name), eq(spoolName), eq(interval * 3));
	}

	@Test
	void completeCycle_onChangeEnabled() throws InterruptedException {
		verifyOnChangeEnabled(times(0), false);
		verifyOnChangeEnabled(times(0), true);

		latch.countDown();

		service.enable();
		verifyOnChangeEnabled(times(0), false);
		verifyOnChangeEnabled(times(1), true);

		service.enable();
		verifyOnChangeEnabled(times(0), false);
		verifyOnChangeEnabled(times(1), true);

		service.disable();
		verifyOnChangeEnabled(times(1), false);
		verifyOnChangeEnabled(times(1), true);

		service.disable();
		verifyOnChangeEnabled(times(1), false);
		verifyOnChangeEnabled(times(1), true);

		service.enable();
		service.disable();
		verifyOnChangeEnabled(times(2), false);
		verifyOnChangeEnabled(times(2), true);

		verify(event, times(4)).onChangeEnabled(eq(name), eq(spoolName), any(boolean.class));
	}

	@Test
	void completeCycle_onChangeRetryAfterTimeFactor() throws InterruptedException {

		verifyOnChangeRetryAfterTimeFactor(times(0));
		service.setRetryAfterTimeFactor(2);
		verifyOnChangeRetryAfterTimeFactor(times(1), 2);

		service.enable();
		service.setRetryAfterTimeFactor(4);
		service.disable();
		verifyOnChangeRetryAfterTimeFactor(times(1), 4);

		verify(event, times(2)).onChangeRetryAfterTimeFactor(eq(name), eq(spoolName), any(double.class));
	}

	private void verifyScheduleNextBackgroundServiceTask(final VerificationMode mode) {
		verify(event, mode).scheduleNextBackgroundServiceTask(
		        any(String.class), any(String.class), any(int.class), any(long.class));
	}

	private void verifyNextBackgroundServiceTask(final VerificationMode mode) {
		verify(event, mode).nextBackgroundServiceTask(
		        any(String.class), any(String.class), any(int.class));
	}

	private void verifyPlanNextExec(final VerificationMode mode) {
		verify(event, mode).planNextExec(
		        any(String.class), any(String.class), any(long.class));
	}

	private void verifyOnPreviousRunWithError(final VerificationMode mode) {
		verify(event, mode).onPreviousRunWithError(
		        any(String.class), any(String.class), any(Exception.class));
	}

	private void verifyOnChangeTimedInterval(final VerificationMode mode) {
		verify(event, mode).onChangeTimedInterval(
		        any(String.class), any(String.class), any(long.class));
	}

	private void verifyOnChangeEnabled(final VerificationMode mode, final boolean expected) {
		verify(event, mode).onChangeEnabled(
		        any(String.class), any(String.class), eq(expected));
	}

	private void verifyOnChangeRetryAfterTimeFactor(final VerificationMode mode) {
		verify(event, mode).onChangeRetryAfterTimeFactor(any(String.class), any(String.class),
		        any(long.class));
	}

	private void verifyOnChangeRetryAfterTimeFactor(final VerificationMode mode, final double retryAfterTimeFactor) {
		verify(event, mode).onChangeRetryAfterTimeFactor(any(String.class), any(String.class),
		        eq(retryAfterTimeFactor));
	}

}
