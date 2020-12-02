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
package tv.hd3g.jobkit.engine.watchfolder;

import static java.time.Duration.ZERO;
import static java.time.Duration.ofDays;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static tv.hd3g.jobkit.engine.watchfolder.WatchFolderPickupType.DIRS_ONLY;
import static tv.hd3g.jobkit.engine.watchfolder.WatchFolderPickupType.FILES_DIRS;
import static tv.hd3g.jobkit.engine.watchfolder.WatchFolderPickupType.FILES_ONLY;

import java.time.Duration;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;

import tv.hd3g.transfertfiles.CachedFileAttributes;

class WatchedFileInMemoryDbTest {

	@Mock
	CachedFileAttributes firstDetectionFile;
	@Mock
	CachedFileAttributes seeAgainFile;

	WatchedFileInMemoryDb f;

	@BeforeEach
	void init() throws Exception {
		MockitoAnnotations.openMocks(this).close();

		when(firstDetectionFile.isDirectory()).thenReturn(false);
		f = new WatchedFileInMemoryDb(firstDetectionFile, FILES_DIRS, ofDays(1));
		verify(firstDetectionFile, Mockito.times(1)).isDirectory();
	}

	@AfterEach
	void end() {
		Mockito.verifyNoMoreInteractions(firstDetectionFile, seeAgainFile);
	}

	@Test
	void testUpdate_updated_inTime() {
		when(firstDetectionFile.lastModified()).thenReturn(1L);
		when(seeAgainFile.lastModified()).thenReturn(2L);

		assertEquals(f, f.update(seeAgainFile));

		verify(firstDetectionFile, Mockito.times(1)).lastModified();
		verify(seeAgainFile, Mockito.times(1)).lastModified();

		assertFalse(f.isQualified());
		assertFalse(f.canBeCallbacked());
		assertTrue(f.isNotYetMarkedAsDone());
		assertEquals(seeAgainFile, f.getLastFile());
	}

	@Test
	void testUpdate_notUpdated_inTime() {
		when(firstDetectionFile.lastModified()).thenReturn(1L);
		when(seeAgainFile.lastModified()).thenReturn(1L);
		when(firstDetectionFile.length()).thenReturn(1L);
		when(seeAgainFile.length()).thenReturn(1L);

		assertEquals(f, f.update(seeAgainFile));

		verify(firstDetectionFile, Mockito.times(1)).lastModified();
		verify(seeAgainFile, Mockito.times(1)).lastModified();
		verify(firstDetectionFile, Mockito.times(1)).length();
		verify(seeAgainFile, Mockito.times(1)).length();

		assertFalse(f.isQualified());
		assertFalse(f.canBeCallbacked());
		assertTrue(f.isNotYetMarkedAsDone());
		assertEquals(seeAgainFile, f.getLastFile());
	}

	@Test
	void testUpdate_updated_OutTime() {
		f = new WatchedFileInMemoryDb(firstDetectionFile, FILES_DIRS, ZERO);
		when(firstDetectionFile.lastModified()).thenReturn(1L);
		when(seeAgainFile.lastModified()).thenReturn(2L);

		assertEquals(f, f.update(seeAgainFile));

		verify(firstDetectionFile, Mockito.times(1)).lastModified();
		verify(seeAgainFile, Mockito.times(1)).lastModified();

		assertFalse(f.isQualified());
		assertFalse(f.canBeCallbacked());
		assertTrue(f.isNotYetMarkedAsDone());
		assertEquals(seeAgainFile, f.getLastFile());

		verify(firstDetectionFile, atLeastOnce()).isDirectory();
	}

	@Test
	void testUpdate_notUpdated_OutTime() {
		f = new WatchedFileInMemoryDb(firstDetectionFile, FILES_DIRS, ZERO);

		when(firstDetectionFile.lastModified()).thenReturn(1L);
		when(seeAgainFile.lastModified()).thenReturn(1L);
		when(firstDetectionFile.length()).thenReturn(1L);
		when(seeAgainFile.length()).thenReturn(1L);

		assertEquals(f, f.update(seeAgainFile));

		verify(firstDetectionFile, Mockito.times(1)).lastModified();
		verify(seeAgainFile, Mockito.times(1)).lastModified();
		verify(firstDetectionFile, Mockito.times(1)).length();
		verify(seeAgainFile, Mockito.times(1)).length();

		assertTrue(f.isQualified());
		assertTrue(f.canBeCallbacked());
		assertTrue(f.isNotYetMarkedAsDone());
		assertEquals(seeAgainFile, f.getLastFile());

		verify(firstDetectionFile, atLeastOnce()).isDirectory();
	}

	@Test
	void testUpdate_afterDone() {
		f.setMarkedAsDone();
		assertEquals(f, f.update(seeAgainFile));
		assertFalse(f.isQualified());
		assertFalse(f.canBeCallbacked());
		assertFalse(f.isNotYetMarkedAsDone());
		assertEquals(firstDetectionFile, f.getLastFile());
	}

	@Test
	void testUpdate_directory() {
		when(firstDetectionFile.isDirectory()).thenReturn(true);
		f = new WatchedFileInMemoryDb(firstDetectionFile, FILES_DIRS, Duration.ofDays(1));
		verify(firstDetectionFile, Mockito.times(2)).isDirectory();

		assertEquals(f, f.update(seeAgainFile));
		assertTrue(f.isQualified());
		assertTrue(f.canBeCallbacked());
		assertTrue(f.isNotYetMarkedAsDone());
		assertEquals(seeAgainFile, f.getLastFile());

		f = new WatchedFileInMemoryDb(firstDetectionFile, FILES_ONLY, ofDays(1));
		assertFalse(f.canBeCallbacked());

		verify(firstDetectionFile, atLeastOnce()).isDirectory();
	}

	@Test
	void testUpdate_directory_afterDone() {
		when(firstDetectionFile.isDirectory()).thenReturn(true);
		f = new WatchedFileInMemoryDb(firstDetectionFile, FILES_DIRS, Duration.ofDays(1));
		verify(firstDetectionFile, Mockito.times(2)).isDirectory();

		f.setMarkedAsDone();
		assertEquals(f, f.update(seeAgainFile));
		assertTrue(f.isQualified());
		assertTrue(f.canBeCallbacked());
		assertFalse(f.isNotYetMarkedAsDone());
		assertEquals(firstDetectionFile, f.getLastFile());
	}

	@Test
	void testUpdate_updatedOnlySizes_OutTime() {
		f = new WatchedFileInMemoryDb(firstDetectionFile, FILES_DIRS, ZERO);

		when(firstDetectionFile.lastModified()).thenReturn(1L);
		when(seeAgainFile.lastModified()).thenReturn(1L);
		when(firstDetectionFile.length()).thenReturn(1L);
		when(seeAgainFile.length()).thenReturn(2L);

		assertEquals(f, f.update(seeAgainFile));

		verify(firstDetectionFile, Mockito.times(1)).lastModified();
		verify(seeAgainFile, Mockito.times(1)).lastModified();
		verify(firstDetectionFile, Mockito.times(1)).length();
		verify(seeAgainFile, Mockito.times(1)).length();

		assertFalse(f.isQualified());
		assertFalse(f.canBeCallbacked());
		assertTrue(f.isNotYetMarkedAsDone());
		assertEquals(seeAgainFile, f.getLastFile());

		verify(firstDetectionFile, atLeastOnce()).isDirectory();
	}

	@Test
	void testIsQualified() {
		assertFalse(f.isQualified());
	}

	@Test
	void testCanBePickup() {
		/** file + FILES_DIRS */
		assertTrue(f.canBePickup());

		/** file */
		f = new WatchedFileInMemoryDb(firstDetectionFile, DIRS_ONLY, ZERO);
		assertFalse(f.canBePickup());

		f = new WatchedFileInMemoryDb(firstDetectionFile, FILES_ONLY, ZERO);
		assertTrue(f.canBePickup());

		/**
		 * dir
		 */
		when(firstDetectionFile.isDirectory()).thenReturn(true);

		f = new WatchedFileInMemoryDb(firstDetectionFile, DIRS_ONLY, ZERO);
		assertTrue(f.canBePickup());

		f = new WatchedFileInMemoryDb(firstDetectionFile, FILES_ONLY, ZERO);
		assertFalse(f.canBePickup());

		verify(firstDetectionFile, atLeastOnce()).isDirectory();
	}

	@Test
	void testSetMarkedAsDone() {
		f.setMarkedAsDone();
		assertFalse(f.isNotYetMarkedAsDone());
	}

	@Test
	void testIsNotYetMarkedAsDone() {
		assertTrue(f.isNotYetMarkedAsDone());
	}

	@Test
	void testAbsentInSet() {
		assertTrue(f.absentInSet(Set.of()));
		assertFalse(f.absentInSet(Set.of(firstDetectionFile)));
		assertTrue(f.absentInSet(Set.of(seeAgainFile)));
	}

}
