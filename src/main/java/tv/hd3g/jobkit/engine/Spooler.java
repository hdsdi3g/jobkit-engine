package tv.hd3g.jobkit.engine;

import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.jobkit.engine.status.SpoolerStatus;

public class Spooler {

	private static Logger log = LogManager.getLogger();

	private final ConcurrentHashMap<String, SpoolExecutor> spoolExecutors;
	private final ExecutionEvent event;
	private final ThreadFactory threadFactory;
	private final AtomicLong threadCount;
	private final AtomicBoolean shutdown;

	public Spooler(final ExecutionEvent event) {
		this.event = event;
		spoolExecutors = new ConcurrentHashMap<>();
		threadCount = new AtomicLong(0);
		shutdown = new AtomicBoolean(false);

		threadFactory = r -> {
			final Thread t = new Thread(r);
			t.setPriority(Thread.MIN_PRIORITY);
			t.setDaemon(false);
			t.setName("SpoolExecutor #" + threadCount.getAndIncrement());
			return t;
		};
	}

	private Stream<SpoolExecutor> getSpoolExecutorStream() {
		return spoolExecutors.entrySet().stream().map(Entry::getValue);
	}

	/**
	 * @return actual or new Executor (it will be created as needed).
	 *         Can't create new if this spooler is shutdown (return null).
	 */
	public SpoolExecutor getExecutor(final String name) {
		if (shutdown.get()) {
			return spoolExecutors.get(name);
		}
		return spoolExecutors.computeIfAbsent(name, n -> new SpoolExecutor(n, event, threadFactory));
	}

	public int getAllQueuesSize() {
		return getSpoolExecutorStream().mapToInt(SpoolExecutor::getQueueSize).sum();
	}

	public int getRunningQueuesCount() {
		return (int) getSpoolExecutorStream().filter(SpoolExecutor::isRunning).count();
	}

	/**
	 * Non-blocking
	 */
	public void shutdown() {
		if (shutdown.get()) {
			return;
		}
		shutdown.set(true);
		log.info("Shutdown all ({}) spoolExecutors. {} are running jobs and {} in waiting.",
		        spoolExecutors.mappingCount(),
		        getRunningQueuesCount(),
		        getAllQueuesSize());
		getSpoolExecutorStream().forEach(SpoolExecutor::shutdown);
		event.shutdownSpooler();
	}

	/**
	 * Blocking. It call shutdown() before.
	 */
	public void waitToClose() {
		shutdown();
		final var count = getRunningQueuesCount();
		if (count > 0) {
			log.info("Wait to ends all current ({}) running jobs...", count);
		}
		getSpoolExecutorStream().forEach(SpoolExecutor::waitToClose);
	}

	public SpoolerStatus getLastStatus() {
		final var allCurrentStatuses = getSpoolExecutorStream()
		        .map(SpoolExecutor::getLastStatus)
		        .collect(Collectors.toSet());
		return new SpoolerStatus(allCurrentStatuses, threadCount.get(), shutdown.get());
	}

}
