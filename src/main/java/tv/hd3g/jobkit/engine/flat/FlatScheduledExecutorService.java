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

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class FlatScheduledExecutorService implements ScheduledExecutorService {
	private static final Logger log = LogManager.getLogger();

	private final Set<FlatScheduledFuture> registedTasks;

	FlatScheduledExecutorService() {
		registedTasks = Collections.synchronizedSet(new HashSet<>());
	}

	public void runAllOnce() {
		log.debug("Run {} scheduledFuture(s)", registedTasks.size());
		new ArrayList<>(registedTasks).forEach(FlatScheduledFuture::run);
	}

	public void add(final FlatScheduledFuture runReference) {
		log.debug("Add scheduledFuture: {}", runReference);
		registedTasks.add(runReference);
	}

	public void remove(final FlatScheduledFuture runReference) {
		log.debug("Remove scheduledFuture: {}", runReference);
		registedTasks.remove(runReference);
	}

	public boolean contain(final FlatScheduledFuture runReference) {
		return registedTasks.contains(runReference);
	}

	public boolean isEmpty() {
		return registedTasks.isEmpty();
	}

	@Override
	public ScheduledFuture<?> schedule(final Runnable command, final long delay, final TimeUnit unit) {
		final var sch = new FlatScheduledFuture(command);
		registedTasks.add(sch);
		return sch;
	}

	@Override
	public ScheduledFuture<?> scheduleAtFixedRate(final Runnable command,
	                                              final long initialDelay,
	                                              final long period,
	                                              final TimeUnit unit) {
		final var sch = new FlatScheduledFuture(command);
		registedTasks.add(sch);
		return sch;
	}

	@Override
	public ScheduledFuture<?> scheduleWithFixedDelay(final Runnable command,
	                                                 final long initialDelay,
	                                                 final long delay,
	                                                 final TimeUnit unit) {
		final var sch = new FlatScheduledFuture(command);
		registedTasks.add(sch);
		return sch;
	}

	@Override
	public <V> ScheduledFuture<V> schedule(final Callable<V> callable, final long delay, final TimeUnit unit) {
		throw new UnsupportedOperationException();
	}

	@Override
	public void shutdown() {
		throw new UnsupportedOperationException();
	}

	@Override
	public List<Runnable> shutdownNow() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isShutdown() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean isTerminated() {
		throw new UnsupportedOperationException();
	}

	@Override
	public boolean awaitTermination(final long timeout, final TimeUnit unit) throws InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> Future<T> submit(final Callable<T> task) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> Future<T> submit(final Runnable task, final T result) {
		throw new UnsupportedOperationException();
	}

	@Override
	public Future<?> submit(final Runnable task) {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks) throws InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> List<Future<T>> invokeAll(final Collection<? extends Callable<T>> tasks,
	                                     final long timeout,
	                                     final TimeUnit unit) throws InterruptedException {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> T invokeAny(final Collection<? extends Callable<T>> tasks) throws InterruptedException, ExecutionException {
		throw new UnsupportedOperationException();
	}

	@Override
	public <T> T invokeAny(final Collection<? extends Callable<T>> tasks,
	                       final long timeout,
	                       final TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
		throw new UnsupportedOperationException();
	}

	@Override
	public void execute(final Runnable command) {
		throw new UnsupportedOperationException();
	}

}
