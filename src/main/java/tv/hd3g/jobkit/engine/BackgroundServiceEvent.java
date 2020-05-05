package tv.hd3g.jobkit.engine;

public interface BackgroundServiceEvent {

	default void scheduleNextBackgroundServiceTask(final String backgroundServiceName,
	                                               final String spoolName,
	                                               final int priority,
	                                               final long interval) {
	}

	default void nextBackgroundServiceTask(final String name, final String spoolName, final int priority) {
	}

	default void planNextExec(final String name, final String spoolName, final long nextInterval) {
	}

	default void onPreviousRunWithError(final String name, final String spoolName, final Exception lastExecException) {
	}

	default void onChangeTimedInterval(final String name, final String spoolName, final long timedInterval) {
	}

	default void onChangeEnabled(final String name, final String spoolName, final boolean enabled) {
	}

	default void onChangeRetryAfterTimeFactor(final String name,
	                                          final String spoolName,
	                                          final double retryAfterTimeFactor) {
	}

}
