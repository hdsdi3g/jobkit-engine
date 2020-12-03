package tv.hd3g.jobkit.engine.status;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import tv.hd3g.jobkit.engine.BackgroundService;

class BackgroundServiceStatusTest {

	static final Random random = new Random();

	@Mock
	BackgroundService backgroundService;

	String name;
	String spoolName;
	boolean enabled;
	long nextRunReferenceDelay;
	long timedInterval;
	long previousScheduledDate;
	int priority;
	double retryAfterTimeFactor;
	int sequentialErrorCount;
	String task;

	BackgroundServiceStatus backgroundServiceStatus;

	@BeforeEach
	void init() throws Exception {
		MockitoAnnotations.openMocks(this).close();

		name = String.valueOf(random.nextLong());
		spoolName = String.valueOf(random.nextLong());
		enabled = random.nextBoolean();
		nextRunReferenceDelay = random.nextLong();
		timedInterval = random.nextLong();
		previousScheduledDate = random.nextLong();
		priority = random.nextInt();
		retryAfterTimeFactor = random.nextDouble();
		sequentialErrorCount = random.nextInt();
		task = String.valueOf(random.nextLong());

		when(backgroundService.isEnabled()).thenReturn(enabled);
		when(backgroundService.getTimedInterval(eq(MILLISECONDS))).thenReturn(timedInterval);
		when(backgroundService.getPriority()).thenReturn(priority);
		when(backgroundService.getRetryAfterTimeFactor()).thenReturn(retryAfterTimeFactor);

		backgroundServiceStatus = new BackgroundServiceStatus(
		        name,
		        spoolName,
		        nextRunReferenceDelay,
		        previousScheduledDate,
		        backgroundService,
		        sequentialErrorCount,
		        task);
	}

	@Test
	void testGetName() {
		assertEquals(name, backgroundServiceStatus.getName());
	}

	@Test
	void testGetSpoolName() {
		assertEquals(spoolName, backgroundServiceStatus.getSpoolName());
	}

	@Test
	void testIsEnabled() {
		assertEquals(enabled, backgroundServiceStatus.isEnabled());
	}

	@Test
	void testGetNextRunReferenceDelay() {
		assertEquals(nextRunReferenceDelay, backgroundServiceStatus.getNextRunReferenceDelay());
	}

	@Test
	void testGetTimedInterval() {
		assertEquals(timedInterval, backgroundServiceStatus.getTimedInterval());
	}

	@Test
	void testGetPreviousScheduledDate() {
		assertEquals(previousScheduledDate, backgroundServiceStatus.getPreviousScheduledDate());
	}

	@Test
	void testGetPriority() {
		assertEquals(priority, backgroundServiceStatus.getPriority());
	}

	@Test
	void testGetRetryAfterTimeFactor() {
		assertEquals(retryAfterTimeFactor, backgroundServiceStatus.getRetryAfterTimeFactor());
	}

	@Test
	void testGetSequentialErrorCount() {
		assertEquals(sequentialErrorCount, backgroundServiceStatus.getSequentialErrorCount());
	}

	@Test
	void testGetTask() {
		assertEquals(task, backgroundServiceStatus.getTask());
	}

}
