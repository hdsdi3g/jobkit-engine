package tv.hd3g.jobkit.engine.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.lang.Thread.State;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import tv.hd3g.jobkit.engine.SpoolJobStatus;

class SpoolExecutorStatusTest {

	@Mock
	List<SpoolJobStatus> queue;

	String spoolName;
	String currentOperationName;
	long currentThreadId;
	State currentThreadState;
	String currentThreadName;
	int queueSize;
	boolean isShutdown;

	private SpoolExecutorStatus spoolExecutorStatus;

	@BeforeEach
	void init() throws Exception {
		MockitoAnnotations.openMocks(this).close();

		spoolName = String.valueOf(System.nanoTime());
		currentOperationName = String.valueOf(System.nanoTime());
		currentThreadId = System.nanoTime();
		currentThreadState = State.TERMINATED;
		currentThreadName = String.valueOf(System.nanoTime());
		queueSize = new Random().nextInt();
		isShutdown = true;

		spoolExecutorStatus = new SpoolExecutorStatus(
		        spoolName,
		        currentOperationName,
		        currentThreadId,
		        currentThreadState,
		        currentThreadName,
		        queue,
		        isShutdown);

	}

	@Test
	void testGetSpoolName() {
		assertEquals(spoolName, spoolExecutorStatus.getSpoolName());
	}

	@Test
	void testGetCurrentOperationName() {
		assertEquals(currentOperationName, spoolExecutorStatus.getCurrentOperationName());
	}

	@Test
	void testGetCurrentThreadId() {
		assertEquals(currentThreadId, spoolExecutorStatus.getCurrentThreadId());
	}

	@Test
	void testGetCurrentThreadState() {
		assertEquals(currentThreadState, spoolExecutorStatus.getCurrentThreadState());
	}

	@Test
	void testGetCurrentThreadName() {
		assertEquals(currentThreadName, spoolExecutorStatus.getCurrentThreadName());
	}

	@Test
	void testGetQueue() {
		assertEquals(queue, spoolExecutorStatus.getQueue());
	}

	@Test
	void testIsShutdown() {
		assertEquals(isShutdown, spoolExecutorStatus.isShutdown());
	}

}
