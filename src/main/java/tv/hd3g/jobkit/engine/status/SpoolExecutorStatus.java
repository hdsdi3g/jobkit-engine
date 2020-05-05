package tv.hd3g.jobkit.engine.status;

import java.lang.Thread.State;
import java.util.List;

import tv.hd3g.jobkit.engine.SpoolJobStatus;

public class SpoolExecutorStatus {

	private final String spoolName;
	private final String currentOperationName;
	private final long currentThreadId;
	private final State currentThreadState;
	private final String currentThreadName;
	private final List<SpoolJobStatus> queue;
	private final boolean isShutdown;

	public SpoolExecutorStatus(final String spoolName,
	                           final String currentOperationName,
	                           final long currentThreadId,
	                           final State currentThreadState,
	                           final String currentThreadName,
	                           final List<SpoolJobStatus> queue,
	                           final boolean isShutdown) {
		this.spoolName = spoolName;
		this.currentOperationName = currentOperationName;
		this.currentThreadId = currentThreadId;
		this.currentThreadState = currentThreadState;
		this.currentThreadName = currentThreadName;
		this.queue = queue;
		this.isShutdown = isShutdown;
	}

	public String getSpoolName() {
		return spoolName;
	}

	public String getCurrentOperationName() {
		return currentOperationName;
	}

	public long getCurrentThreadId() {
		return currentThreadId;
	}

	public State getCurrentThreadState() {
		return currentThreadState;
	}

	public String getCurrentThreadName() {
		return currentThreadName;
	}

	public List<SpoolJobStatus> getQueue() {
		return queue;
	}

	public boolean isShutdown() {
		return isShutdown;
	}

}
