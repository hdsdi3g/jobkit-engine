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

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.util.Random;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

class WatchedFilesTest {

	@Mock
	Set<File> founded;
	@Mock
	Set<File> losted;
	int totalFiles;

	WatchedFiles watchedFiles;

	@BeforeEach
	void init() throws Exception {
		MockitoAnnotations.openMocks(this).close();
		totalFiles = new Random().nextInt();
		watchedFiles = new WatchedFiles(founded, losted, totalFiles);
	}

	@Test
	void testGetFounded() {
		assertEquals(founded, watchedFiles.getFounded());
	}

	@Test
	void testGetLosted() {
		assertEquals(losted, watchedFiles.getLosted());
	}

	@Test
	void testGetTotalFiles() {
		assertEquals(totalFiles, watchedFiles.getTotalFiles());
	}
}
