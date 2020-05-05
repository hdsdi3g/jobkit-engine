package tv.hd3g.jobkit.engine.status;

import java.util.Set;

public class SpoolerStatus {

	private final Set<SpoolExecutorStatus> spoolExecutors;
	private final long createdThreadsCount;
	private final boolean shutdown;

	public SpoolerStatus(final Set<SpoolExecutorStatus> spoolExecutors,
	                     final long createdThreadsCount,
	                     final boolean shutdown) {
		this.spoolExecutors = spoolExecutors;
		this.createdThreadsCount = createdThreadsCount;
		this.shutdown = shutdown;
	}

	public Set<SpoolExecutorStatus> getSpoolExecutors() {
		return spoolExecutors;
	}

	public long getCreatedThreadsCount() {
		return createdThreadsCount;
	}

	public boolean isShutdown() {
		return shutdown;
	}
}
