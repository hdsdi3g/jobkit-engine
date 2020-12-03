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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;

import java.util.concurrent.Callable;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

class FlatScheduledExecutorServiceTest {

	final Callable<Void> callable = null;
	@Mock
	FlatScheduledFuture runReference;

	FlatScheduledExecutorService service;
	Runnable task;

	@BeforeEach
	void init() throws Exception {
		MockitoAnnotations.openMocks(this).close();
		service = new FlatScheduledExecutorService();
		task = () -> {
		};
	}

	@Test
	void testRunAllOnce() {
		service.add(runReference);
		service.runAllOnce();
		verify(runReference, Mockito.times(1)).run();

		service.remove(runReference);
		service.runAllOnce();
		verify(runReference, Mockito.times(1)).run();
	}

	@Test
	void testContain() {
		assertFalse(service.contain(runReference));
		service.add(runReference);
		assertTrue(service.contain(runReference));
		service.remove(runReference);
		assertFalse(service.contain(runReference));
	}

	@Test
	void testIsEmpty() {
		assertTrue(service.isEmpty());
		service.add(runReference);
		assertFalse(service.isEmpty());
		service.remove(runReference);
		assertTrue(service.isEmpty());
	}

	@Test
	void testScheduleRunnableLongTimeUnit() {
		final var schF = service.schedule(task, 0, TimeUnit.DAYS);
		assertEquals(new FlatScheduledFuture(task), schF);
		assertTrue(service.contain((FlatScheduledFuture) schF));
	}

	@Test
	void testScheduleAtFixedRate() {
		final var schF = service.scheduleAtFixedRate(task, 0, 0, TimeUnit.DAYS);
		assertEquals(new FlatScheduledFuture(task), schF);
		assertTrue(service.contain((FlatScheduledFuture) schF));
	}

	@Test
	void testScheduleWithFixedDelay() {
		final var schF = service.scheduleWithFixedDelay(task, 0, 0, TimeUnit.DAYS);
		assertEquals(new FlatScheduledFuture(task), schF);
		assertTrue(service.contain((FlatScheduledFuture) schF));
	}

	@Test
	void testScheduleCallableOfVLongTimeUnit() {
		assertThrows(UnsupportedOperationException.class, () -> service.schedule(callable, 0, null));
	}

	@Test
	void testShutdown() {
		assertThrows(UnsupportedOperationException.class, () -> service.shutdown());
	}

	@Test
	void testShutdownNow() {
		assertThrows(UnsupportedOperationException.class, () -> service.shutdownNow());
	}

	@Test
	void testIsShutdown() {
		assertThrows(UnsupportedOperationException.class, () -> service.isShutdown());
	}

	@Test
	void testIsTerminated() {
		assertThrows(UnsupportedOperationException.class, () -> service.isTerminated());
	}

	@Test
	void testAwaitTermination() {
		assertThrows(UnsupportedOperationException.class, () -> service.awaitTermination(0, TimeUnit.DAYS));
	}

	@Test
	void testSubmitCallableOfT() {
		assertThrows(UnsupportedOperationException.class, () -> service.submit(callable));
	}

	@Test
	void testSubmitRunnableT() {
		assertThrows(UnsupportedOperationException.class, () -> service.submit(task, null));
	}

	@Test
	void testSubmitRunnable() {
		assertThrows(UnsupportedOperationException.class, () -> service.submit(task));
	}

	@Test
	void testInvokeAllCollectionOfQextendsCallableOfT() {
		assertThrows(UnsupportedOperationException.class, () -> service.invokeAll(null));
	}

	@Test
	void testInvokeAllCollectionOfQextendsCallableOfTLongTimeUnit() {
		assertThrows(UnsupportedOperationException.class, () -> service.invokeAll(null, 0, TimeUnit.DAYS));
	}

	@Test
	void testInvokeAnyCollectionOfQextendsCallableOfT() {
		assertThrows(UnsupportedOperationException.class, () -> service.invokeAny(null));
	}

	@Test
	void testInvokeAnyCollectionOfQextendsCallableOfTLongTimeUnit() {
		assertThrows(UnsupportedOperationException.class, () -> service.invokeAny(null, 0, TimeUnit.DAYS));
	}

	@Test
	void testExecute() {
		assertThrows(UnsupportedOperationException.class, () -> service.execute(task));
	}
}
