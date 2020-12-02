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

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.File;
import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import tv.hd3g.commons.IORuntimeException;
import tv.hd3g.jobkit.engine.flat.FlatJobKitEngine;
import tv.hd3g.transfertfiles.AbstractFileSystemURL;

class WatchfoldersTest {

	@Mock
	FolderActivity folderActivity;
	@Mock
	WatchedFilesDb watchedFilesDb;
	@Mock
	WatchFolderPickupType pickUp;
	@Mock
	WatchedFiles watchedFiles;

	ObservedFolder observedFolder;
	FlatJobKitEngine jobKitEngine;

	Watchfolders watchfolders;

	@BeforeEach
	void init() throws Exception {
		MockitoAnnotations.openMocks(this).close();
		observedFolder = new ObservedFolder();
		observedFolder.setLabel("Internal test");
		observedFolder.setTargetFolder("file://localhost/" + new File("").getAbsolutePath());
		jobKitEngine = new FlatJobKitEngine();

		when(folderActivity.getPickUpType(eq(observedFolder))).thenReturn(pickUp);
		when(watchedFilesDb.update(any(AbstractFileSystemURL.class))).thenReturn(watchedFiles);
	}

	@AfterEach
	void close() throws InterruptedException {
		verify(watchedFilesDb, times(1)).setup(eq(observedFolder), eq(pickUp));
	}

	@Test
	void testMissingActiveFolder_duringRun() {
		watchfolders = new Watchfolders(List.of(observedFolder), folderActivity,
		        Duration.ofMillis(1), jobKitEngine, "default", "default", () -> watchedFilesDb);

		verify(folderActivity, times(0)).onScanErrorFolder(any(ObservedFolder.class));

		observedFolder.setTargetFolder("file://localhost/this/not/exists");
		assertTrue(jobKitEngine.isEmptyActiveServicesList());
		watchfolders.startScans();

		jobKitEngine.runAllServicesOnce();

		assertFalse(jobKitEngine.isEmptyActiveServicesList());
		verify(folderActivity, times(1)).onScanErrorFolder(eq(observedFolder));

		assertThrows(IORuntimeException.class, () -> jobKitEngine.runAllServicesOnce());

		assertFalse(jobKitEngine.isEmptyActiveServicesList());
		verify(folderActivity, times(1)).onScanErrorFolder(eq(observedFolder));

		assertThrows(IORuntimeException.class, () -> jobKitEngine.runAllServicesOnce());

		/**
		 * Back to normal
		 */
		observedFolder.setTargetFolder("file://localhost/" + new File("").getAbsolutePath());
		jobKitEngine.runAllServicesOnce();
		verify(folderActivity, times(1)).onStartScans(eq(List.of(observedFolder)));
		verify(folderActivity, times(1)).onScanErrorFolder(eq(observedFolder));
	}

	@Test
	void testStartStopScans() {
		watchfolders = new Watchfolders(List.of(observedFolder), folderActivity,
		        Duration.ofMillis(1), jobKitEngine, "default", "default", () -> watchedFilesDb);
		assertTrue(jobKitEngine.isEmptyActiveServicesList());

		watchfolders.startScans();
		jobKitEngine.runAllServicesOnce();

		assertFalse(jobKitEngine.isEmptyActiveServicesList());
		verify(folderActivity, times(1)).onStartScans(eq(List.of(observedFolder)));
		verify(folderActivity, times(1)).onBeforeScan(eq(observedFolder));
		verify(watchedFilesDb, times(1)).update(any(AbstractFileSystemURL.class));
		verify(folderActivity, times(1)).onAfterScan(eq(observedFolder), any(Duration.class), eq(watchedFiles));
		verify(folderActivity, times(0)).onStopScans(eq(List.of(observedFolder)));

		watchfolders.stopScans();
		jobKitEngine.runAllServicesOnce();

		assertTrue(jobKitEngine.isEmptyActiveServicesList());
		verify(folderActivity, times(1)).onStartScans(eq(List.of(observedFolder)));
		verify(folderActivity, times(1)).onBeforeScan(eq(observedFolder));
		verify(watchedFilesDb, times(1)).update(any(AbstractFileSystemURL.class));
		verify(folderActivity, times(1)).onAfterScan(eq(observedFolder), any(Duration.class), eq(watchedFiles));
		verify(folderActivity, times(1)).onStopScans(eq(List.of(observedFolder)));

		watchfolders.startScans();
		watchfolders.startScans();
		jobKitEngine.runAllServicesOnce();

		assertFalse(jobKitEngine.isEmptyActiveServicesList());
		verify(folderActivity, times(2)).onStartScans(eq(List.of(observedFolder)));
		verify(folderActivity, times(2)).onBeforeScan(eq(observedFolder));
		verify(watchedFilesDb, times(2)).update(any(AbstractFileSystemURL.class));
		verify(folderActivity, times(2)).onAfterScan(eq(observedFolder), any(Duration.class), eq(watchedFiles));
		verify(folderActivity, times(1)).onStopScans(eq(List.of(observedFolder)));

		watchfolders.stopScans();
		watchfolders.stopScans();
		jobKitEngine.runAllServicesOnce();

		assertTrue(jobKitEngine.isEmptyActiveServicesList());
		verify(folderActivity, times(2)).onStartScans(eq(List.of(observedFolder)));
		verify(folderActivity, times(2)).onBeforeScan(eq(observedFolder));
		verify(watchedFilesDb, times(2)).update(any(AbstractFileSystemURL.class));
		verify(folderActivity, times(2)).onAfterScan(eq(observedFolder), any(Duration.class), eq(watchedFiles));
		verify(folderActivity, times(2)).onStopScans(eq(List.of(observedFolder)));

		verify(folderActivity, times(0)).onScanErrorFolder(any(ObservedFolder.class));
	}

	@Test
	void testGetService() {
		watchfolders = new Watchfolders(List.of(observedFolder), folderActivity,
		        Duration.ofMillis(1), jobKitEngine, "default", "default", () -> watchedFilesDb);
		assertNull(watchfolders.getService());

		watchfolders.startScans();
		assertNotNull(watchfolders.getService());
		watchfolders.stopScans();
		assertNull(watchfolders.getService());
	}
}
