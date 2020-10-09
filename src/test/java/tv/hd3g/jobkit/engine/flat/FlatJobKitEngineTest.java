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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;

import tv.hd3g.jobkit.engine.Job;

class FlatJobKitEngineTest {

	FlatJobKitEngine jobKitEngine;
	Runnable task;

	@BeforeEach
	void init() {
		jobKitEngine = new FlatJobKitEngine();
		task = () -> {
		};

		/**
		 * Do nothing
		 */
		jobKitEngine.shutdown();
		jobKitEngine.waitToClose();
	}

	@Test
	void testCreateService() {
		assertNotNull(jobKitEngine.createService(null, null, task));
	}

	@Test
	void testStartServiceStringStringLongTimeUnitRunnable() {
		assertNotNull(jobKitEngine.startService(null, null, 0, TimeUnit.DAYS, task));
	}

	@Test
	void testStartServiceStringStringDurationRunnable() {
		assertNotNull(jobKitEngine.startService(null, null, Duration.ZERO, task));
	}

	@Test
	void testGetSpooler() {
		assertThrows(UnsupportedOperationException.class, () -> jobKitEngine.getSpooler());
	}

	@Test
	void testGetLastStatus() {
		assertThrows(UnsupportedOperationException.class, () -> jobKitEngine.getLastStatus());
	}

	@Test
	void testRunAllServicesOnce() {
		final var i = new AtomicInteger();
		task = () -> i.getAndIncrement();
		jobKitEngine.startService(null, null, 0, TimeUnit.DAYS, task);
		jobKitEngine.runAllServicesOnce();
		assertEquals(1, i.get());
	}

	@Test
	void testIsEmptyActiveServicesList() {
		assertTrue(jobKitEngine.isEmptyActiveServicesList());

		var s = jobKitEngine.createService(null, null, task);
		assertTrue(jobKitEngine.isEmptyActiveServicesList());
		s.enable();
		assertFalse(jobKitEngine.isEmptyActiveServicesList());
		s.disable();
		assertTrue(jobKitEngine.isEmptyActiveServicesList());
		s = jobKitEngine.startService(null, null, 0, TimeUnit.DAYS, task);
		assertFalse(jobKitEngine.isEmptyActiveServicesList());
		s.disable();
		assertTrue(jobKitEngine.isEmptyActiveServicesList());
	}

	@Test
	void testRunOneShot() {
		final var i = new AtomicInteger();
		final var j = new AtomicInteger();
		task = () -> i.getAndIncrement();
		final Consumer<Exception> afterRunCommand = e -> j.getAndIncrement();

		assertTrue(jobKitEngine.runOneShot(null, null, 0, task, afterRunCommand));
		assertEquals(1, i.get());
		assertEquals(1, j.get());
	}

	@Test
	void testRunOneShotJob() {
		final var job = Mockito.mock(Job.class);
		assertTrue(jobKitEngine.runOneShot(job));

		verify(job, Mockito.times(1)).onJobStart();
		verify(job, Mockito.times(1)).onJobDone();
		verify(job, Mockito.times(1)).run();
		verify(job, Mockito.times(0)).onJobFail(ArgumentMatchers.any(Exception.class));
	}

}
