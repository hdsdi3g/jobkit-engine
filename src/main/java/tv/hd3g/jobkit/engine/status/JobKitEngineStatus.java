package tv.hd3g.jobkit.engine.status;

import java.util.Set;

public class JobKitEngineStatus {

	private final SpoolerStatus spoolerStatus;
	private final Set<BackgroundServiceStatus> backgroundServicesStatus;

	public JobKitEngineStatus(final SpoolerStatus spoolerStatus,
	                          final Set<BackgroundServiceStatus> backgroundServicesStatus) {
		this.spoolerStatus = spoolerStatus;
		this.backgroundServicesStatus = backgroundServicesStatus;
	}

	public Set<BackgroundServiceStatus> getBackgroundServicesStatus() {
		return backgroundServicesStatus;
	}

	public SpoolerStatus getSpoolerStatus() {
		return spoolerStatus;
	}

}
