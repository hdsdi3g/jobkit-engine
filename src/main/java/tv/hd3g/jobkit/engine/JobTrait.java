package tv.hd3g.jobkit.engine;

import java.util.function.Consumer;

public interface JobTrait {

	/**
	 * @return true if the task is queued
	 */
	boolean runOneShot(final String name,
	                   final String spoolName,
	                   final int priority,
	                   final Runnable task,
	                   final Consumer<Exception> afterRunCommand);

	/**
	 * @return true if the task is queued
	 */
	default boolean runOneShot(final Job job) {
		final Runnable run = () -> {
			job.onJobStart();
			job.run();
		};

		final Consumer<Exception> afterRunCommand = e -> {
			if (e != null) {
				job.onJobFail(e);
			} else {
				job.onJobDone();
			}
		};

		return runOneShot(job.getJobName(), job.getJobSpoolname(), job.getJobPriority(), run, afterRunCommand);
	}

}
