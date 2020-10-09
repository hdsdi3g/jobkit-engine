package tv.hd3g.jobkit.engine;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.time.Duration;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import tv.hd3g.jobkit.engine.status.JobKitEngineStatus;

public class JobKitEngine implements JobTrait {

	private final ConcurrentHashMap<String, BackgroundService> backgroundServices;
	private final ScheduledExecutorService scheduledExecutor;
	private final BackgroundServiceEvent backgroundServiceEvent;
	private final Spooler spooler;

	public JobKitEngine(final ScheduledExecutorService scheduledExecutor,
	                    final ExecutionEvent executionEvent,
	                    final BackgroundServiceEvent backgroundServiceEvent) {
		this.scheduledExecutor = scheduledExecutor;
		this.backgroundServiceEvent = backgroundServiceEvent;
		spooler = new Spooler(executionEvent);
		backgroundServices = new ConcurrentHashMap<>();
	}

	protected JobKitEngine() {
		scheduledExecutor = null;
		backgroundServiceEvent = null;
		spooler = null;
		backgroundServices = null;
	}

	/**
	 * @return true if the task is queued
	 */
	@Override
	public boolean runOneShot(final String name,
	                          final String spoolName,
	                          final int priority,
	                          final Runnable task,
	                          final Consumer<Exception> afterRunCommand) {
		return spooler.getExecutor(spoolName).addToQueue(task, name, priority, afterRunCommand);
	}

	/**
	 * @return a new service or the existing service for "name"
	 */
	public BackgroundService createService(final String name, final String spoolName, final Runnable task) {
		return backgroundServices.computeIfAbsent(spoolName,
		        sName -> new BackgroundService(name,
		                sName,
		                spooler,
		                scheduledExecutor,
		                backgroundServiceEvent,
		                task));
	}

	/**
	 * Create a service if not exists as "name".
	 */
	public BackgroundService startService(final String name,
	                                      final String spoolName,
	                                      final long timedInterval,
	                                      final TimeUnit unit,
	                                      final Runnable task) {
		return createService(name, spoolName, task)
		        .setTimedInterval(timedInterval, unit)
		        .enable();
	}

	/**
	 * Create a service if not exists as "name".
	 */
	public BackgroundService startService(final String name,
	                                      final String spoolName,
	                                      final Duration duration,
	                                      final Runnable task) {
		return startService(name, spoolName, duration.toMillis(), MILLISECONDS, task);
	}

	public Spooler getSpooler() {
		return spooler;
	}

	/**
	 * Stop all services and shutdown spooler.
	 * Non-blocking.
	 * Don't forget to shutdown the scheduled executor.
	 */
	public void shutdown() {
		backgroundServices.entrySet().stream()
		        .forEach(bS -> bS.getValue().disable());
		spooler.shutdown();
	}

	/**
	 * Blocking. It call shutdown() before.
	 */
	public void waitToClose() {
		shutdown();
		spooler.waitToClose();
	}

	public JobKitEngineStatus getLastStatus() {
		final var spoolerStatus = spooler.getLastStatus();
		final var backgroundServicesStatus = backgroundServices.entrySet().stream()
		        .map(entry -> entry.getValue().getLastStatus())
		        .collect(Collectors.toUnmodifiableSet());
		return new JobKitEngineStatus(spoolerStatus, backgroundServicesStatus);
	}

}
