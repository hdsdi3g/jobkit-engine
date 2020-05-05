package tv.hd3g.jobkit.engine;

public interface Job extends Runnable {

	String getJobName();

	String getJobSpoolname();

	default int getJobPriority() {
		return 0;
	}

	default void onJobStart() {
	}

	default void onJobDone() {
	}

	default void onJobFail(final Exception e) {
	}

}
