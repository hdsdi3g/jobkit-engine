package tv.hd3g.jobkit.engine.status;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class SpoolerStatusTest {

	@Mock
	private Set<SpoolExecutorStatus> spoolExecutors;
	private long createdThreadsCount;

	private SpoolerStatus spoolerStatus;

	@BeforeEach
	void init() throws Exception {
		MockitoAnnotations.openMocks(this).close();
		createdThreadsCount = System.nanoTime();
		spoolerStatus = new SpoolerStatus(spoolExecutors, createdThreadsCount, true);
	}

	@Test
	void testGetSpoolExecutors() {
		assertEquals(spoolExecutors, spoolerStatus.getSpoolExecutors());
	}

	@Test
	void testGetCreatedThreadsCount() {
		assertEquals(createdThreadsCount, spoolerStatus.getCreatedThreadsCount());
	}

	@Test
	void testIsShutdown() {
		assertTrue(spoolerStatus.isShutdown());
	}

}
