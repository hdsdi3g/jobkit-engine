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

import static java.nio.file.StandardOpenOption.APPEND;
import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.SYNC;
import static java.nio.file.StandardOpenOption.WRITE;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static tv.hd3g.jobkit.engine.watchfolder.WatchFolderPickupType.DIRS_ONLY;
import static tv.hd3g.jobkit.engine.watchfolder.WatchFolderPickupType.FILES_DIRS;
import static tv.hd3g.jobkit.engine.watchfolder.WatchFolderPickupType.FILES_ONLY;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Collection;
import java.util.Set;

import org.apache.commons.io.FileUtils;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import tv.hd3g.commons.IORuntimeException;
import tv.hd3g.transfertfiles.AbstractFileSystemURL;
import tv.hd3g.transfertfiles.CachedFileAttributes;
import tv.hd3g.transfertfiles.local.LocalFile;

class WatchedFilesInMemoryDbTest {

	static final File rootDir = new File("target/test-" + WatchedFilesInMemoryDbTest.class.getSimpleName());

	@BeforeAll
	static void prepare() throws IOException {
		FileUtils.forceMkdir(rootDir);
		FileUtils.cleanDirectory(rootDir);
	}

	File workingFile;
	ObservedFolder observedFolder;
	WatchedFilesInMemoryDb watchedFilesDb;
	AbstractFileSystemURL fs;

	@BeforeEach
	void init() throws IOException {
		observedFolder = new ObservedFolder();
		workingFile = new File(rootDir, String.valueOf(Math.abs(System.nanoTime())));
		observedFolder.setTargetFolder("file://localhost/" + workingFile.getAbsolutePath());
		if (workingFile.exists()) {
			throw new IOException("Temp dir exists: " + workingFile);
		}
		FileUtils.forceMkdir(workingFile);

		observedFolder.setLabel("test");
		observedFolder.setAllowedExtentions(Set.of("ok"));
		observedFolder.setBlockedExtentions(Set.of("no"));
		observedFolder.setIgnoreRelativePaths(Set.of("never/here"));
		observedFolder.setIgnoreFiles(Set.of("desktop.ini", ".DS_Store", "ignoreme.ok"));

		watchedFilesDb = new WatchedFilesInMemoryDb();
		fs = observedFolder.createFileSystem();
	}

	@Test
	void testUpdate_found() {
		watchedFilesDb.setup(observedFolder, FILES_ONLY);
		var w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 0);

		write("thisfine.ok", "thisnotfine.no", "/whysubdir/thisfine.ok", "ignoreme.ok");

		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 1);

		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of("thisfine.ok"), Set.of(), 1);

		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 1);

		delete("thisfine.ok");
		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 0);
	}

	@Test
	void testUpdate_updates_size() {
		watchedFilesDb.setup(observedFolder, FILES_ONLY);

		write("thisfine2.ok");
		var w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 1);

		write("thisfine2.ok");
		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 1);

		write("thisfine2.ok");
		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 1);

		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of("thisfine2.ok"), Set.of(), 1);
	}

	@Test
	void testUpdate_updates_dates() throws IOException, InterruptedException {
		watchedFilesDb.setup(observedFolder, FILES_ONLY);

		write("thisfine5.ok");
		var w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 1);

		Thread.sleep(100);// NOSONAR
		FileUtils.touch(toAbsolutePath("thisfine5.ok"));
		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 1);

		Thread.sleep(100);// NOSONAR
		FileUtils.touch(toAbsolutePath("thisfine5.ok"));
		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 1);

		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of("thisfine5.ok"), Set.of(), 1);
	}

	@Test
	void testUpdate_lost() {
		watchedFilesDb.setup(observedFolder, FILES_ONLY);

		write("thisfine3.ok");
		var w = watchedFilesDb.update(fs);

		delete("thisfine3.ok");
		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of("thisfine3.ok"), 0);
	}

	@Test
	void testUpdate_subdir() {
		observedFolder.setRecursive(true);
		watchedFilesDb.setup(observedFolder, FILES_ONLY);
		assertEquals(10, watchedFilesDb.getMaxDeep());

		write("thisfine.ok", "/oksubdir/thisfine.ok", "/sub/sub/dir/anotherfine.ok");

		var w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 3);

		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w,
		        Set.of("thisfine.ok", "/oksubdir/thisfine.ok", "/sub/sub/dir/anotherfine.ok"),
		        Set.of(), 3);
	}

	@Test
	void testUpdate_onlyDir() {
		observedFolder.setRecursive(true);
		watchedFilesDb.setup(observedFolder, DIRS_ONLY);
		assertEquals(10, watchedFilesDb.getMaxDeep());

		write("thisfine.ok", "/oksubdir/thisfine.ok", "/sub/sub/dir/anotherfine.ok");

		var w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 4);

		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w,
		        Set.of("/oksubdir", "/sub", "/sub/sub", "/sub/sub/dir"),
		        Set.of(), 4);
	}

	@Test
	void testUpdate_FilesDir() {
		observedFolder.setRecursive(true);
		watchedFilesDb.setup(observedFolder, FILES_DIRS);
		assertEquals(10, watchedFilesDb.getMaxDeep());

		write("thisfine.ok", "/oksubdir/thisfine.ok", "/sub/sub/dir/anotherfine.ok");

		var w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 4 + 3);

		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w,
		        Set.of("/oksubdir", "/sub", "/sub/sub", "/sub/sub/dir",
		                "thisfine.ok", "/oksubdir/thisfine.ok", "/sub/sub/dir/anotherfine.ok"),
		        Set.of(), 4 + 3);
	}

	@Test
	void testUpdate_hidden() {
		watchedFilesDb.setup(observedFolder, FILES_ONLY);

		write("thisfine.ok", ".thisnotfine.ok");
		var w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 1);
		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of("thisfine.ok"), Set.of(), 1);

		observedFolder.setAllowedHidden(true);
		watchedFilesDb = new WatchedFilesInMemoryDb();
		watchedFilesDb.setup(observedFolder, FILES_ONLY);

		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 2);
		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of("thisfine.ok", ".thisnotfine.ok"), Set.of(), 2);
	}

	@Test
	void testUpdate_noExtentionsMgm() {
		observedFolder.setAllowedExtentions(null);
		observedFolder.setBlockedExtentions(null);
		watchedFilesDb.setup(observedFolder, FILES_ONLY);

		write("thisfine");
		var w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 1);
		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of("thisfine"), Set.of(), 1);
	}

	@Test
	void testUpdate_relativePaths() {
		observedFolder.setRecursive(true);
		watchedFilesDb.setup(observedFolder, FILES_ONLY);

		write("thisfine.ok", "/never/here/thisfine.ok");
		var w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 1);
		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of("thisfine.ok"), Set.of(), 1);

		observedFolder.setIgnoreRelativePaths(null);
		watchedFilesDb = new WatchedFilesInMemoryDb();
		watchedFilesDb.setup(observedFolder, FILES_ONLY);

		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of(), Set.of(), 2);
		w = watchedFilesDb.update(fs);
		assertWatchedFiles(w, Set.of("thisfine.ok", "/never/here/thisfine.ok"), Set.of(), 2);
	}

	private File toAbsolutePath(final String relativePath) {
		return new File(workingFile, relativePath).getAbsoluteFile();
	}

	private Set<File> toAbsolutePath(final Collection<String> relativePaths) {
		return relativePaths.stream()
		        .map(this::toAbsolutePath)
		        .collect(toUnmodifiableSet());
	}

	private void write(final String... relativePath) {
		for (var pos = 0; pos < relativePath.length; pos++) {
			write(relativePath[pos]);
		}
	}

	private void write(final String relativePath) {
		try {
			final var f = toAbsolutePath(relativePath);
			FileUtils.forceMkdirParent(f);
			Files.write(f.toPath(), Set.of(String.valueOf(System.nanoTime())), APPEND, SYNC, WRITE, CREATE);
		} catch (final IOException e) {
			throw new IORuntimeException(e);
		}
	}

	private void delete(final String relativePath) {
		try {
			final var f = toAbsolutePath(relativePath);
			if (f.exists()) {
				FileUtils.forceDelete(f);
			}
		} catch (final IOException e) {
			throw new IORuntimeException(e);
		}
	}

	private void assertWatchedFiles(final WatchedFiles watchedFiles,
	                                final Set<String> founded,
	                                final Set<String> losted,
	                                final int totalFiles) {
		assertNotNull(watchedFiles);
		final var currentRootDir = workingFile.getName();
		assertEquals(toAbsolutePath(founded), revealRealFiles(watchedFiles.getFounded()),
		        "Search founded; root dir: " + currentRootDir);
		assertEquals(toAbsolutePath(losted), revealRealFiles(watchedFiles.getLosted()),
		        "Search losted; root dir: " + currentRootDir);
		assertEquals(totalFiles, watchedFiles.getTotalFiles(),
		        "Search totalFiles; root dir: " + currentRootDir);

		try {
			Thread.sleep(1);// NOSONAR S2925
		} catch (final InterruptedException e) {
		}
	}

	private Set<File> revealRealFiles(final Set<CachedFileAttributes> fileAttr) {
		return fileAttr.stream()
		        .map(CachedFileAttributes::getAbstractFile)
		        .map(af -> (LocalFile) af)
		        .map(LocalFile::getInternalFile)
		        .collect(toUnmodifiableSet());
	}

	@Test
	void testSetup() {
		observedFolder.setRecursive(false);
		watchedFilesDb.setup(observedFolder, FILES_DIRS);
		assertEquals(0, watchedFilesDb.getMaxDeep());

		observedFolder.setRecursive(true);
		watchedFilesDb = new WatchedFilesInMemoryDb();
		watchedFilesDb.setup(observedFolder, FILES_DIRS);
		assertEquals(10, watchedFilesDb.getMaxDeep());
	}

	@Test
	void testGetMaxDeep() {
		assertEquals(10, watchedFilesDb.getMaxDeep());
	}

	@Test
	void testSetMaxDeep() {
		watchedFilesDb.setMaxDeep(5);
		assertEquals(5, watchedFilesDb.getMaxDeep());
	}

}
