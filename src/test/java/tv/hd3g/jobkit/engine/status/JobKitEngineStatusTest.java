package tv.hd3g.jobkit.engine.status;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class JobKitEngineStatusTest {

	@Mock
	SpoolerStatus spoolerStatus;
	@Mock
	Set<BackgroundServiceStatus> backgroundServicesStatus;

	JobKitEngineStatus jobKitEngineStatus;

	@BeforeEach
	void init() {
		MockitoAnnotations.initMocks(this);
		jobKitEngineStatus = new JobKitEngineStatus(spoolerStatus, backgroundServicesStatus);
	}

	@Test
	void testGetBackgroundServicesStatus() {
		assertEquals(backgroundServicesStatus, jobKitEngineStatus.getBackgroundServicesStatus());
	}

	@Test
	void testGetSpoolerStatus() {
		assertEquals(spoolerStatus, jobKitEngineStatus.getSpoolerStatus());
	}

}
