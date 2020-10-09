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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class FlatBackgroundServiceTest {

	FlatScheduledExecutorService scheduledExecutor;
	Runnable task;

	FlatBackgroundService flatBackgroundService;

	@BeforeEach
	void init() {
		scheduledExecutor = new FlatScheduledExecutorService();
		task = () -> {
		};
		flatBackgroundService = new FlatBackgroundService(scheduledExecutor, task);
	}

	@Test
	void testEnable() {
		flatBackgroundService.enable();
		assertTrue(scheduledExecutor.contain(new FlatScheduledFuture(task)));
	}

	@Test
	void testDisable() {
		flatBackgroundService.enable();
		flatBackgroundService.disable();
		assertFalse(scheduledExecutor.contain(new FlatScheduledFuture(task)));
	}

	@Test
	void testIsEnabled() {
		assertFalse(flatBackgroundService.isEnabled());
		flatBackgroundService.enable();
		assertTrue(flatBackgroundService.isEnabled());
	}

	@Test
	void testSetTimedIntervalLongTimeUnit() {
		assertNotNull(flatBackgroundService.setTimedInterval(0, TimeUnit.DAYS));
	}

	@Test
	void testSetTimedIntervalDuration() {
		assertNotNull(flatBackgroundService.setTimedInterval(Duration.ZERO));
	}

	@Test
	void testSetRetryAfterTimeFactor() {
		assertNotNull(flatBackgroundService.setRetryAfterTimeFactor(0));
	}

	@Test
	void testGetLastStatus() {
		assertThrows(UnsupportedOperationException.class, () -> flatBackgroundService.getLastStatus());
	}

}
