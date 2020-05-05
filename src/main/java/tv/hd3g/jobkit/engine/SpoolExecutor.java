package tv.hd3g.jobkit.engine;

import static java.util.Optional.ofNullable;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.jobkit.engine.status.SpoolExecutorStatus;

public class SpoolExecutor {

	private static Logger log = LogManager.getLogger();

	private final String name;
	private final ExecutionEvent event;
	private final ThreadFactory threadFactory;

	private Thread currentOperation;
	private String currentOperationName;
	private final Comparator<SpoolJob> queueComparator;
	private final PriorityBlockingQueue<SpoolJob> queue;
	private final AtomicBoolean shutdown;

	public SpoolExecutor(final String name, final ExecutionEvent event, final ThreadFactory threadFactory) {
		this.name = name;
		this.event = event;
		this.threadFactory = threadFactory;
		queueComparator = (l, r) -> Integer.compare(r.priority, l.priority);
		queue = new PriorityBlockingQueue<>(1, queueComparator);
		shutdown = new AtomicBoolean(false);
	}

	// SpoolJobStatus

	public boolean addToQueue(final Runnable command,
	                          final String name,
	                          final int priority,
	                          final Consumer<Exception> afterRunCommand) {
		if (shutdown.get()) {
			log.error("Can't add to queue new command \"{}\" by \"{}\": the spool is shutdown", name, this.name);
			return false;
		}

		if (queue.offer(new SpoolJob(command, name, priority, afterRunCommand, this)) == false) {
			throw new IllegalStateException("Can't submit a new task in queue");
		}
		log.debug("Add new command \"{}\" by \"{}\" with P{}", name, this.name, priority);
		runNext();
		return true;
	}

	public int getQueueSize() {
		return queue.size();
	}

	public boolean isRunning() {
		return currentOperation != null && currentOperation.isAlive();
	}

	private void runNext() {
		if (shutdown.get()) {
			return;
		}
		synchronized (queue) {
			if (currentOperation != null && currentOperation.isAlive()) {
				return;
			}
			final var next = queue.poll();
			if (next == null) {
				currentOperation = null;
				currentOperationName = null;
				return;
			}
			currentOperation = threadFactory.newThread(next);
			currentOperationName = next.commandName;
			currentOperation.start();
		}
	}

	/**
	 * Non-blocking
	 */
	public void shutdown() {
		log.debug("Set shutdown for {}", name);
		shutdown.set(true);
		queue.clear();
	}

	/**
	 * Blocking
	 */
	public void waitToClose() {
		if (shutdown.get() == false) {
			shutdown();
		} else if (currentOperation == null) {
			return;
		}
		log.debug("Wait to close {}...", name);

		try {
			while (currentOperation.isAlive()) {
				Thread.onSpinWait();
			}
		} catch (final NullPointerException e) {// NOSONAR
		}
		log.debug("{} is now closed", name);
	}

	private class SpoolJob implements Runnable, SpoolJobStatus {

		final Runnable command;
		final String commandName;
		final int priority;
		final Consumer<Exception> afterRunCommand;
		final SpoolExecutor executorReferer;

		SpoolJob(final Runnable command,
		         final String commandName,
		         final int priority,
		         final Consumer<Exception> afterRunCommand,
		         final SpoolExecutor executorReferer) {
			this.command = command;
			this.commandName = commandName;
			this.priority = priority;
			this.afterRunCommand = afterRunCommand;
			this.executorReferer = executorReferer;
		}

		@Override
		public void run() {
			try {
				event.beforeStart(commandName, System.currentTimeMillis(), executorReferer);
			} catch (final Exception e) {
				log.warn("Can't send event BeforeStart", e);
			}

			final long startTime = System.currentTimeMillis();
			Exception error = null;
			try {
				log.info("Start new command \"{}\" by \"{}\"", commandName, name);
				command.run();
				log.info("Ends correcly command \"{}\" by \"{}\", after {} sec", commandName, name,
				        (System.currentTimeMillis() - startTime) / 1000f);
			} catch (final Exception e) {
				error = e;
				log.warn("Command \"{}\" by \"{}\", failed after {} sec", commandName, name,
				        (System.currentTimeMillis() - startTime) / 1000f, e);
			}

			final long endTime = System.currentTimeMillis();
			try {
				if (error != null) {
					event.afterFailedRun(commandName, endTime, endTime - startTime, executorReferer, error);
				} else {
					event.afterRunCorrectly(commandName, endTime, endTime - startTime, executorReferer);
				}
			} catch (final Exception e) {
				log.warn("Can't send event afterRun", e);
			}

			try {
				log.debug("Start to run afterRunCommand for  \"{}\" by \"{}\"", commandName, name);
				afterRunCommand.accept(error);
			} catch (final Exception e) {
				log.error("Fail to run afterRunCommand for  \"{}\" by \"{}\"", commandName, name, e);
			}

			synchronized (queue) {
				currentOperation = null;
				currentOperationName = null;
			}
			runNext();
		}

		@Override
		public String getName() {
			return commandName;
		}

		@Override
		public String getSpoolName() {
			return name;
		}

		@Override
		public int getPriority() {
			return priority;
		}

	}

	public SpoolExecutorStatus getLastStatus() {
		synchronized (queue) {
			return new SpoolExecutorStatus(name,
			        currentOperationName,
			        ofNullable(currentOperation).map(Thread::getId).orElse(-1l),
			        ofNullable(currentOperation).map(Thread::getState).orElse(null),
			        ofNullable(currentOperation).map(Thread::getName).orElse(null),
			        queue.stream().sorted(queueComparator).collect(Collectors.toUnmodifiableList()),
			        shutdown.get());
		}
	}

}
