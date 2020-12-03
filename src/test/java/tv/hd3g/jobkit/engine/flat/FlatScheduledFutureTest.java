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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class FlatScheduledFutureTest {

	@Mock
	Runnable task;
	FlatScheduledFuture future;

	@BeforeEach
	void init() throws Exception {
		MockitoAnnotations.openMocks(this).close();
		future = new FlatScheduledFuture(task);
	}

	@Test
	void testHashCode() {
		assertEquals(new FlatScheduledFuture(task).hashCode(), future.hashCode());
	}

	@Test
	void testRun() {
		future.run();
		verify(task, Mockito.timeout(1)).run();
	}

	@Test
	void testEqualsObject() {
		assertEquals(new FlatScheduledFuture(task), future);
	}

	@Test
	void testGetDelay() {
		assertThrows(UnsupportedOperationException.class, () -> future.getDelay(null));
	}

	@Test
	void testCompareTo() {
		assertThrows(UnsupportedOperationException.class, () -> future.compareTo(null));
	}

	@Test
	void testCancel() {
		assertThrows(UnsupportedOperationException.class, () -> future.cancel(false));
	}

	@Test
	void testIsCancelled() {
		assertThrows(UnsupportedOperationException.class, () -> future.isCancelled());
	}

	@Test
	void testIsDone() {
		assertThrows(UnsupportedOperationException.class, () -> future.isDone());
	}

	@Test
	void testGet() {
		assertThrows(UnsupportedOperationException.class, () -> future.get());
	}

	@Test
	void testGetLongTimeUnit() {
		assertThrows(UnsupportedOperationException.class, () -> future.get(0, null));
	}
}
