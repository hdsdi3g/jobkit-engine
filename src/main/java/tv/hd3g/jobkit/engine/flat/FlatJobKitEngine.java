/*
 * This file is part of jobkit-engine.
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU Lesser General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU Lesser General Public License for more details.
 *
 * Copyright (C) hdsdi3g for hd3g.tv 2020
 *
 */
package tv.hd3g.jobkit.engine.flat;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.jobkit.engine.BackgroundService;
import tv.hd3g.jobkit.engine.Job;
import tv.hd3g.jobkit.engine.JobKitEngine;
import tv.hd3g.jobkit.engine.Spooler;
import tv.hd3g.jobkit.engine.status.JobKitEngineStatus;

public class FlatJobKitEngine extends JobKitEngine {
	private static final Logger log = LogManager.getLogger();
	private final FlatScheduledExecutorService flatShExecutor;

	public FlatJobKitEngine() {
		super();
		flatShExecutor = new FlatScheduledExecutorService();
	}

	public void runAllServicesOnce() {
		flatShExecutor.runAllOnce();
	}

	public boolean isEmptyActiveServicesList() {
		return flatShExecutor.isEmpty();
	}

	@Override
	public BackgroundService createService(final String name, final String spoolName, final Runnable task) {
		log.debug("Create service {}, spool {}", name, spoolName);
		return new FlatBackgroundService(flatShExecutor, task);
	}

	@Override
	public BackgroundService startService(final String name,
	                                      final String spoolName,
	                                      final Duration duration,
	                                      final Runnable task) {
		log.debug("Start service {}, spool {}", name, spoolName);
		return createService(name, spoolName, task).setTimedInterval(duration).enable();
	}

	@Override
	public BackgroundService startService(final String name,
	                                      final String spoolName,
	                                      final long timedInterval,
	                                      final TimeUnit unit,
	                                      final Runnable task) {
		log.debug("Start service {}, spool {}", name, spoolName);
		return createService(name, spoolName, task).setTimedInterval(timedInterval, unit).enable();
	}

	@Override
	public Spooler getSpooler() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean runOneShot(final Job job) {
		job.onJobStart();
		job.run();
		job.onJobDone();
		return true;
	}

	@Override
	public boolean runOneShot(final String name,
	                          final String spoolName,
	                          final int priority,
	                          final Runnable task,
	                          final Consumer<Exception> afterRunCommand) {
		task.run();
		afterRunCommand.accept(null);
		return true;
	}

	@Override
	public void shutdown() {
		/**
		 * Not needed, not implemented
		 */
	}

	@Override
	public void waitToClose() {
		/**
		 * Not needed, not implemented
		 */
	}

	@Override
	public JobKitEngineStatus getLastStatus() {
		throw new UnsupportedOperationException();
	}

}
