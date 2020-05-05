package tv.hd3g.jobkit.engine;

public interface ExecutionEvent {

	default void beforeStart(final String commandName,
	                         final long startTime,
	                         final SpoolExecutor executorReferer) {
	}

	default void afterRunCorrectly(final String commandName,
	                               final long endTime,
	                               final long duration,
	                               final SpoolExecutor executorReferer) {
	}

	default void afterFailedRun(final String commandName,
	                            final long endTime,
	                            final long duration,
	                            final SpoolExecutor executorReferer,
	                            final Exception error) {
	}

	default void shutdownSpooler() {
	}

}
