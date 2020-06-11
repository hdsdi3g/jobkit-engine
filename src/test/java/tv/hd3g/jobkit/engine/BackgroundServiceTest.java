package tv.hd3g.jobkit.engine;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.util.Random;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class BackgroundServiceTest {

	static Random random = new Random();

	@Mock
	Spooler spooler;
	@Mock
	ScheduledExecutorService scheduledExecutor;
	@Mock
	BackgroundServiceEvent event;
	@Mock
	Runnable task;
	@Mock
	ScheduledFuture<Object> nextRunReference;
	@Mock
	SpoolExecutor spoolExecutor;

	@Captor
	ArgumentCaptor<Runnable> commandCaptor;
	@Captor
	ArgumentCaptor<Consumer<Exception>> afterRunCommandCaptor;
	@Captor
	ArgumentCaptor<Runnable> scheduleCommandCaptor;

	String name;
	String spoolName;
	BackgroundService backgroundService;
	long timedInterval;

	@BeforeEach
	void init() {
		MockitoAnnotations.initMocks(this);
		name = String.valueOf(System.nanoTime());
		spoolName = String.valueOf(System.nanoTime());
		timedInterval = Math.abs(random.nextLong());
		backgroundService = new BackgroundService(name, spoolName, spooler, scheduledExecutor, event, task);

		when(scheduledExecutor.schedule(any(Runnable.class), eq(timedInterval), eq(MILLISECONDS)))
		        .then(invocation -> nextRunReference);
		when(spooler.getExecutor(eq(spoolName))).thenReturn(spoolExecutor);
		when(nextRunReference.isDone()).thenReturn(false);
		when(nextRunReference.isCancelled()).thenReturn(false);
	}

	@Test
	void testGetPriority() {
		assertEquals(0, backgroundService.getPriority());
	}

	@Test
	void testIsEnabled() {
		assertFalse(backgroundService.isEnabled());
	}

	@Test
	void testSetPriority() {
		final var priority = random.nextInt();
		final var bck = backgroundService.setPriority(priority);
		assertEquals(backgroundService, bck);
		assertEquals(priority, backgroundService.getPriority());
	}

	@Test
	void testEnable() {
		assertThrows(IllegalArgumentException.class, () -> {
			backgroundService.enable();
		});
	}

	@Test
	void testDisable() {
		final var bck = backgroundService.disable();
		assertEquals(backgroundService, bck);
	}

	@Test
	void testSetTimedIntervalLongTimeUnit() {
		final var bck = backgroundService.setTimedInterval(timedInterval, MILLISECONDS);
		assertEquals(backgroundService, bck);
		assertEquals(timedInterval, backgroundService.getTimedInterval(MILLISECONDS));

		Mockito.verify(scheduledExecutor, Mockito.never())
		        .schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
	}

	@Test
	void testSetTimedIntervalLongTimeUnit_invalidTime() {
		backgroundService.setTimedInterval(timedInterval, MILLISECONDS);
		assertThrows(IllegalArgumentException.class, () -> backgroundService.setTimedInterval(0, MILLISECONDS));
		assertEquals(timedInterval, backgroundService.getTimedInterval(MILLISECONDS));

		Mockito.verify(scheduledExecutor, Mockito.never())
		        .schedule(any(Runnable.class), anyLong(), any(TimeUnit.class));
	}

	@Test
	void testGetTimedInterval() {
		assertEquals(0, backgroundService.getTimedInterval(MILLISECONDS));
	}

	@Test
	void testSetTimedIntervalDuration() {
		final var bck = backgroundService.setTimedInterval(Duration.ofMillis(timedInterval));
		assertEquals(backgroundService, bck);
		assertEquals(timedInterval, backgroundService.getTimedInterval(MILLISECONDS));
	}

	@Test
	void testGetTimedIntervalDuration() {
		final var tid = backgroundService.getTimedIntervalDuration();
		assertNotNull(tid);
		assertTrue(tid.isZero());
	}

	@Test
	void testStartup() {
		backgroundService.setTimedInterval(timedInterval, MILLISECONDS).enable();
		verify(scheduledExecutor, only())
		        .schedule(scheduleCommandCaptor.capture(), eq(timedInterval), eq(MILLISECONDS));

		final var scheduledCommand = scheduleCommandCaptor.getValue();
		assertNotNull(scheduledCommand);

		scheduledCommand.run();
		when(nextRunReference.isDone()).thenReturn(true);

		verify(spooler, only()).getExecutor(eq(spoolName));
		verify(spoolExecutor, only())
		        .addToQueue(commandCaptor.capture(), eq(name), eq(0), afterRunCommandCaptor.capture());

		assertEquals(task, commandCaptor.getValue());

		afterRunCommandCaptor.getValue().accept(null);

		verify(scheduledExecutor, Mockito.times(2))
		        .schedule(scheduleCommandCaptor.capture(), eq(timedInterval), eq(MILLISECONDS));

		when(nextRunReference.isDone()).thenReturn(false);
		final var value = afterRunCommandCaptor.getValue();
		assertThrows(IllegalStateException.class, () -> value.accept(null));
	}

	@Test
	void testStartup_errors() {
		timedInterval = 1000;
		backgroundService.setTimedInterval(timedInterval, MILLISECONDS).enable().setRetryAfterTimeFactor(10);
		verify(scheduledExecutor, only())
		        .schedule(scheduleCommandCaptor.capture(), eq(timedInterval), eq(MILLISECONDS));
		final var scheduledCommand = scheduleCommandCaptor.getValue();
		assertNotNull(scheduledCommand);

		scheduledCommand.run();

		verify(spoolExecutor, only())
		        .addToQueue(any(Runnable.class), eq(name), eq(0), afterRunCommandCaptor.capture());

		final var lastException = new Exception();
		afterRunCommandCaptor.getValue().accept(lastException);

		verify(scheduledExecutor, times(1))
		        .schedule(scheduleCommandCaptor.capture(), eq(timedInterval * 10), eq(MILLISECONDS));

		scheduleCommandCaptor.getValue().run();
		afterRunCommandCaptor.getValue().accept(lastException);

		verify(scheduledExecutor, times(1))
		        .schedule(scheduleCommandCaptor.capture(), eq(timedInterval * 100), eq(MILLISECONDS));
	}

	@Test
	void testStartup_errors_resolved() {
		timedInterval = 1000;
		backgroundService.setTimedInterval(timedInterval, MILLISECONDS).enable().setRetryAfterTimeFactor(10);
		verify(scheduledExecutor, only())
		        .schedule(scheduleCommandCaptor.capture(), eq(timedInterval), eq(MILLISECONDS));

		scheduleCommandCaptor.getValue().run();
		verify(spoolExecutor, only())
		        .addToQueue(any(Runnable.class), eq(name), eq(0), afterRunCommandCaptor.capture());
		afterRunCommandCaptor.getValue().accept(new Exception());

		verify(scheduledExecutor, times(1))
		        .schedule(scheduleCommandCaptor.capture(), eq(timedInterval * 10), eq(MILLISECONDS));

		scheduleCommandCaptor.getValue().run();
		afterRunCommandCaptor.getValue().accept(null);

		verify(scheduledExecutor, times(2))
		        .schedule(scheduleCommandCaptor.capture(), eq(timedInterval), eq(MILLISECONDS));
	}

	@Test
	void testStartup_disable() {
		backgroundService.setTimedInterval(timedInterval, MILLISECONDS).enable();

		verify(scheduledExecutor, times(1))
		        .schedule(any(Runnable.class), eq(timedInterval), eq(MILLISECONDS));

		backgroundService.disable();

		assertFalse(backgroundService.isEnabled());
		assertEquals(timedInterval, backgroundService.getTimedInterval(TimeUnit.MILLISECONDS));

		verify(scheduledExecutor, times(1))
		        .schedule(any(Runnable.class), eq(timedInterval), eq(MILLISECONDS));
		verify(nextRunReference, times(1)).cancel(eq(false));
	}

	@Test
	void testStartup_disable_enable() {
		backgroundService.setTimedInterval(timedInterval, MILLISECONDS).enable().disable().enable();

		assertTrue(backgroundService.isEnabled());
		assertEquals(timedInterval, backgroundService.getTimedInterval(TimeUnit.MILLISECONDS));

		verify(scheduledExecutor, times(2))
		        .schedule(any(Runnable.class), eq(timedInterval), eq(MILLISECONDS));
		verify(nextRunReference, times(1)).cancel(eq(false));
	}

	@Test
	void testStartup_changeTimedInterval() {
		timedInterval = TimeUnit.HOURS.toMillis(1);
		when(scheduledExecutor.schedule(any(Runnable.class), eq(timedInterval), eq(MILLISECONDS)))
		        .then(invocation -> nextRunReference);
		when(nextRunReference.getDelay(eq(MILLISECONDS))).thenReturn(timedInterval);

		backgroundService.setTimedInterval(timedInterval, MILLISECONDS).enable();
		backgroundService.setTimedInterval(timedInterval * 2l, MILLISECONDS);

		assertTrue(backgroundService.isEnabled());
		assertEquals(timedInterval * 2l, backgroundService.getTimedInterval(TimeUnit.MILLISECONDS));

		verify(scheduledExecutor, times(1))
		        .schedule(any(Runnable.class), eq(timedInterval), eq(MILLISECONDS));
		verify(scheduledExecutor, times(1))
		        .schedule(any(Runnable.class), eq(timedInterval * 2l), eq(MILLISECONDS));
		verify(nextRunReference, times(1)).cancel(eq(false));
	}

	@Test
	void testStartup_changeTimedInterval_duringRun() {
		timedInterval = TimeUnit.HOURS.toMillis(1);
		when(scheduledExecutor.schedule(any(Runnable.class), eq(timedInterval), eq(MILLISECONDS)))
		        .then(invocation -> nextRunReference);
		when(nextRunReference.getDelay(eq(MILLISECONDS))).thenReturn(timedInterval);

		backgroundService.setTimedInterval(timedInterval, MILLISECONDS).enable();
		verify(scheduledExecutor, times(1))
		        .schedule(scheduleCommandCaptor.capture(), eq(timedInterval), eq(MILLISECONDS));

		final var scheduleCommand = scheduleCommandCaptor.getValue();
		assertNotNull(scheduleCommand);
		scheduleCommand.run();
		when(nextRunReference.isDone()).thenReturn(true);

		backgroundService.setTimedInterval(timedInterval * 2l, MILLISECONDS);

		verify(scheduledExecutor, only())
		        .schedule(any(Runnable.class), eq(timedInterval), eq(MILLISECONDS));
		verify(nextRunReference, never()).cancel(eq(false));

		verify(spoolExecutor, only())
		        .addToQueue(any(Runnable.class), eq(name), eq(0), afterRunCommandCaptor.capture());
		afterRunCommandCaptor.getValue().accept(null);

		verify(scheduledExecutor, times(1))
		        .schedule(any(Runnable.class), eq(timedInterval * 2l), eq(MILLISECONDS));
	}

	@Test
	void testStartup_changeTimedInterval_invalid() {
		backgroundService.setTimedInterval(timedInterval, MILLISECONDS).enable();
		assertThrows(IllegalArgumentException.class, () -> backgroundService.setTimedInterval(0, MILLISECONDS));

		verify(scheduledExecutor, times(1))
		        .schedule(any(Runnable.class), anyLong(), eq(MILLISECONDS));
	}

	@Test
	void testStartup_disable_duringRun() {
		backgroundService.setTimedInterval(timedInterval, MILLISECONDS).enable();
		verify(scheduledExecutor, times(1))
		        .schedule(scheduleCommandCaptor.capture(), eq(timedInterval), eq(MILLISECONDS));

		final var scheduleCommand = scheduleCommandCaptor.getValue();
		assertNotNull(scheduleCommand);
		scheduleCommand.run();
		when(nextRunReference.isDone()).thenReturn(true);

		backgroundService.disable();

		verify(scheduledExecutor, only())
		        .schedule(any(Runnable.class), eq(timedInterval), eq(MILLISECONDS));
		verify(nextRunReference, never()).cancel(eq(false));

		verify(spoolExecutor, only())
		        .addToQueue(any(Runnable.class), eq(name), eq(0), afterRunCommandCaptor.capture());
		afterRunCommandCaptor.getValue().accept(null);

		verify(scheduledExecutor, times(1))
		        .schedule(any(Runnable.class), eq(timedInterval), eq(MILLISECONDS));
	}

	@Test
	void testGetRetryAfterTimeFactor() {
		assertEquals(1, backgroundService.getRetryAfterTimeFactor());
	}

	@Test
	void testSetRetryAfterTimeFactor() {
		final var retryAfterTimeFactor = Math.abs(random.nextInt());
		backgroundService.setRetryAfterTimeFactor(retryAfterTimeFactor);
		assertEquals(retryAfterTimeFactor, backgroundService.getRetryAfterTimeFactor());

		assertThrows(IllegalArgumentException.class, () -> backgroundService.setRetryAfterTimeFactor(-1));
		assertThrows(IllegalArgumentException.class, () -> backgroundService.setRetryAfterTimeFactor(0));
	}

	@Test
	void testGetLastStatus() {
		final var beforeAll = System.currentTimeMillis();
		final var statusOff = backgroundService.getLastStatus();
		assertNotNull(statusOff);

		assertEquals(name, statusOff.getName());
		assertEquals(spoolName, statusOff.getSpoolName());
		assertEquals(false, statusOff.isEnabled());
		assertEquals(-1, statusOff.getNextRunReferenceDelay());
		assertEquals(0, statusOff.getTimedInterval());
		assertEquals(0, statusOff.getPreviousScheduledDate());
		assertEquals(0, statusOff.getPriority());
		assertEquals(1d, statusOff.getRetryAfterTimeFactor());
		assertEquals(0, statusOff.getSequentialErrorCount());
		assertNotNull(statusOff.getTask());

		backgroundService.setTimedInterval(timedInterval, MILLISECONDS).enable();
		verify(scheduledExecutor, only())
		        .schedule(scheduleCommandCaptor.capture(), eq(timedInterval), eq(MILLISECONDS));
		scheduleCommandCaptor.getValue().run();
		when(nextRunReference.isDone()).thenReturn(true);
		verify(spoolExecutor, only())
		        .addToQueue(commandCaptor.capture(), eq(name), eq(0), afterRunCommandCaptor.capture());
		afterRunCommandCaptor.getValue().accept(null);

		final var statusOn = backgroundService.getLastStatus();
		assertNotNull(statusOn);

		assertEquals(name, statusOn.getName());
		assertEquals(spoolName, statusOn.getSpoolName());
		assertEquals(true, statusOn.isEnabled());
		assertEquals(0, statusOn.getNextRunReferenceDelay());
		assertEquals(timedInterval, statusOn.getTimedInterval());
		assertTrue(beforeAll <= statusOn.getPreviousScheduledDate());
		assertEquals(0, statusOn.getPriority());
		assertEquals(1d, statusOn.getRetryAfterTimeFactor());
		assertEquals(0, statusOn.getSequentialErrorCount());
		assertNotNull(statusOn.getTask());
	}

}
