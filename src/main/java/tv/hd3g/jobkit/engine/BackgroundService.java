package tv.hd3g.jobkit.engine;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.time.Duration;
import java.util.Date;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.jobkit.engine.status.BackgroundServiceStatus;

public class BackgroundService {

	private static Logger log = LogManager.getLogger();

	private final String name;
	private final String spoolName;
	private final Spooler spooler;
	private final ScheduledExecutorService scheduledExecutor;
	private final BackgroundServiceEvent event;
	private final Runnable task;

	private boolean enabled;
	private ScheduledFuture<?> nextRunReference;
	private long timedInterval;
	private long previousScheduledDate;
	private int priority;
	private double retryAfterTimeFactor;
	private final AtomicInteger sequentialErrorCount;

	public BackgroundService(final String name,
	                         final String spoolName,
	                         final Spooler spooler,
	                         final ScheduledExecutorService scheduledExecutor,
	                         final BackgroundServiceEvent event,
	                         final Runnable task) {
		this.name = name;
		this.spoolName = spoolName;
		this.spooler = spooler;
		this.scheduledExecutor = scheduledExecutor;
		this.event = event;
		this.task = task;
		enabled = false;
		priority = 0;
		retryAfterTimeFactor = 1;
		sequentialErrorCount = new AtomicInteger(0);
	}

	private void ifNextRunReferenceScheduled(final Runnable ifReady) {
		if (nextRunReference != null
		    && nextRunReference.isDone() == false
		    && nextRunReference.isCancelled() == false) {
			ifReady.run();
		}
	}

	private synchronized void planNextExec(final long interval) {
		ifNextRunReferenceScheduled(() -> {
			throw new IllegalStateException("Beware, the nextRunReference for \"" + name + "\" is still active (in "
			                                + nextRunReference.getDelay(TimeUnit.MILLISECONDS) + ")");
		});
		if (enabled == false) {
			throw new IllegalStateException("Beware, this service is not enabled (" + name + ")");
		}
		event.scheduleNextBackgroundServiceTask(name, spoolName, priority, interval);
		nextRunReference = scheduledExecutor.schedule(() -> {
			event.nextBackgroundServiceTask(name, spoolName, priority);
			spooler.getExecutor(spoolName).addToQueue(task, name, priority, lastExecException -> {
				if (enabled == false) {
					return;
				}
				long nextInterval;
				if (lastExecException != null) {
					event.onPreviousRunWithError(name, spoolName, lastExecException);
					nextInterval = Math.round(timedInterval
					                          * Math.pow(retryAfterTimeFactor, sequentialErrorCount.incrementAndGet()));
					planNextExec(nextInterval);
				} else {
					sequentialErrorCount.set(0);
					nextInterval = timedInterval;
					planNextExec(nextInterval);
				}
				log.debug("Schedule for {} the next run to {} sec, the {}", name, nextInterval / 1000d,
				        new Date(System.currentTimeMillis() + nextInterval));
				event.planNextExec(name, spoolName, nextInterval);
			});
		}, interval, TimeUnit.MILLISECONDS);
		previousScheduledDate = System.currentTimeMillis();
	}

	private synchronized void refreshInternalState(final boolean newEnabled, final long newTimedInterval) {
		if (newTimedInterval == 0 && timedInterval > 0) {
			throw new IllegalArgumentException("Invalid time interval of 0");
		}
		if (newEnabled) {
			refreshToEnabledMode(newTimedInterval);
		} else {
			refreshToDisabledMode(newTimedInterval);
		}
	}

	private void refreshToEnabledMode(final long newTimedInterval) {
		if (enabled) {
			/**
			 * Still enabled
			 */
			if (newTimedInterval != timedInterval) {
				log.info("Change Service interval time \"{}\", from {} to {}", name, timedInterval,
				        newTimedInterval);
				ifNextRunReferenceScheduled(() -> {
					final var eta = timedInterval - nextRunReference.getDelay(TimeUnit.MILLISECONDS);
					if (newTimedInterval > eta) {
						/**
						 * Extend interval: replan next time newTimedInterval-eta
						 */
						nextRunReference.cancel(false);
						nextRunReference = null;
						planNextExec(newTimedInterval - eta);
					}
				});
				timedInterval = newTimedInterval;
				event.onChangeTimedInterval(name, spoolName, timedInterval);
			}
		} else {
			/**
			 * Wan't to enable
			 */
			if (timedInterval == 0) {
				throw new IllegalArgumentException("Invalid time interval of 0");
			}
			log.info("Enable Service \"{}\" for each {}", name, timedInterval);
			enabled = true;
			event.onChangeEnabled(name, spoolName, enabled);
			planNextExec(timedInterval);
		}
	}

	private void refreshToDisabledMode(final long newTimedInterval) {
		if (enabled) {
			/**
			 * Wan't to disable
			 */
			log.info("Disable Service \"{}\"", name);
			enabled = false;
			event.onChangeEnabled(name, spoolName, enabled);
			ifNextRunReferenceScheduled(() -> {
				nextRunReference.cancel(false);
				nextRunReference = null;
			});
		} else {
			/**
			 * Don't wan't to enable
			 */
			timedInterval = newTimedInterval;
			event.onChangeTimedInterval(name, spoolName, timedInterval);
		}
	}

	public int getPriority() {
		return priority;
	}

	public BackgroundService setPriority(final int priority) {
		this.priority = priority;
		return this;
	}

	public synchronized BackgroundService enable() {
		refreshInternalState(true, timedInterval);
		return this;
	}

	public synchronized BackgroundService disable() {
		refreshInternalState(false, timedInterval);
		return this;
	}

	public synchronized boolean isEnabled() {
		return enabled;
	}

	public synchronized BackgroundService setTimedInterval(final long timedInterval, final TimeUnit unit) {
		refreshInternalState(enabled, unit.toMillis(timedInterval));
		return this;
	}

	public synchronized long getTimedInterval(final TimeUnit unit) {
		return unit.convert(timedInterval, TimeUnit.MILLISECONDS);
	}

	public synchronized BackgroundService setTimedInterval(final Duration duration) {
		refreshInternalState(enabled, duration.toMillis());
		return this;
	}

	protected synchronized void setInternalTimedInterval(final Duration duration) {
		timedInterval = duration.toMillis();
	}

	public synchronized Duration getTimedIntervalDuration() {
		return Duration.ofMillis(timedInterval);
	}

	/**
	 * @param retryAfterTimeFactor amp. factor for retry with a previously error
	 *        Next delay = timedInterval * retryAfterTimeFactor ^ (error count)
	 *        With retryAfterTimeFactor = 1 and timedInterval = 1,000
	 *        - delay retry will be the same (1000)
	 *        With retryAfterTimeFactor = 2
	 *        - 2000, 4000, 8000, 16000, 32000
	 *        With retryAfterTimeFactor = 5
	 *        - 5000, 25000, 125000, 625000, 3125000, 15625000 (aprox. 260 min)
	 *        With retryAfterTimeFactor = 10
	 *        - 10000, 100000, 1000000, 10000000, 100000000, (aprox. 28 hrs)
	 */
	public synchronized BackgroundService setRetryAfterTimeFactor(final double retryAfterTimeFactor) {
		if (retryAfterTimeFactor <= 0d) {
			throw new IllegalArgumentException("Invalid retryAfterTimeFactor: " + retryAfterTimeFactor);
		}
		this.retryAfterTimeFactor = retryAfterTimeFactor;
		event.onChangeRetryAfterTimeFactor(name, spoolName, retryAfterTimeFactor);
		return this;
	}

	protected synchronized void setInternalRetryAfterTimeFactor(final double retryAfterTimeFactor) {
		this.retryAfterTimeFactor = retryAfterTimeFactor;
	}

	public synchronized double getRetryAfterTimeFactor() {
		return retryAfterTimeFactor;
	}

	public synchronized BackgroundServiceStatus getLastStatus() {
		long nextRunReferenceDelay;
		if (nextRunReference != null) {
			nextRunReferenceDelay = nextRunReference.getDelay(MILLISECONDS);
		} else {
			nextRunReferenceDelay = -1;
		}

		final var taskClass = task.getClass().getName();
		String sTask;
		if (task.toString().contains(taskClass)) {
			sTask = task.toString();
		} else {
			sTask = task.toString() + " [" + taskClass + "]";
		}

		return new BackgroundServiceStatus(name, spoolName, nextRunReferenceDelay, previousScheduledDate,
		        this, sequentialErrorCount.get(), sTask);
	}

}
