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

import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.apache.commons.io.FilenameUtils.getExtension;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import org.apache.commons.io.FileUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

/**
 * Not thread safe
 */
public class WatchedFilesInMemoryDb implements WatchedFilesDb {
	private static final Logger log = LogManager.getLogger();

	private final Map<File, WatchedFile> allWatchedFiles;

	private int maxDeep;
	private ObservedFolder observedFolder;
	private int rootFolderPathSize;
	private boolean pickUpFiles;
	private boolean pickUpDirs;
	private Duration minFixedStateTime;

	public WatchedFilesInMemoryDb() {
		allWatchedFiles = new HashMap<>();
		maxDeep = 10;
	}

	public int getMaxDeep() {
		return maxDeep;
	}

	public void setMaxDeep(final int maxDeep) {
		this.maxDeep = maxDeep;
	}

	@Override
	public void setup(final ObservedFolder observedFolder, final WatchFolderPickupType pickUp) {
		this.observedFolder = observedFolder;
		observedFolder.postConfiguration();
		pickUpFiles = pickUp.isPickUpFiles();
		pickUpDirs = pickUp.isPickUpDirs();
		minFixedStateTime = observedFolder.getMinFixedStateTime();
		rootFolderPathSize = observedFolder.getActiveFolder().getAbsolutePath().length();

		if (observedFolder.isRecursive() == false) {
			maxDeep = 0;
		}
		log.debug("Setup WFDB for {}, pickUpFiles: {}, pickUpDirs: {}, minFixedStateTime: {}, maxDeep: {}",
		        observedFolder.getLabel(), pickUpFiles, pickUpDirs, minFixedStateTime, maxDeep);
	}

	private class WatchedFile {
		final File file;
		final boolean isDirectory;
		long lastWatched;
		long lastDate;
		long lastSize;
		boolean founded;
		boolean justCreated;

		WatchedFile(final File file) {
			this.file = file;
			isDirectory = file.isDirectory();
			lastWatched = System.currentTimeMillis();
			lastDate = file.lastModified();
			lastSize = file.length();
			justCreated = true;
			founded = isDirectory && pickUpDirs == false || isDirectory == false && pickUpFiles == false;

			log.trace("Create WatchedFile for {}", file);
		}

		boolean updateAndIsNotToRecentScan() {
			if (justCreated) {
				justCreated = false;
				return false;
			}
			return lastWatched < System.currentTimeMillis() - minFixedStateTime.toMillis();
		}

		boolean isNotFounded() {
			return founded == false;
		}

		boolean notExists() {
			return file.exists() == false;
		}

		void setFounded() {
			founded = true;
		}

		boolean isValidatedAfterUpdate() {
			if (isDirectory) {
				return true;
			}

			final var same = lastDate == file.lastModified() && lastSize == file.length();
			if (same == false) {
				lastDate = file.lastModified();
				lastSize = file.length();
				lastWatched = System.currentTimeMillis();
				founded = false;
			}
			log.trace("isValidatedAfterUpdate {}: {}", same, file);
			return same;
		}

	}

	@Override
	public WatchedFiles update() {
		final var detected = new HashSet<File>();
		actualScan(observedFolder.getActiveFolder(), maxDeep, detected);

		final var allFounded = detected.stream()
		        .map(fD -> allWatchedFiles.computeIfAbsent(fD, WatchedFile::new))
		        .filter(WatchedFile::isNotFounded)
		        .filter(WatchedFile::updateAndIsNotToRecentScan)
		        .filter(WatchedFile::isValidatedAfterUpdate)
		        .filter(wf -> wf.file.isDirectory() && pickUpDirs
		                      || wf.file.isDirectory() == false && pickUpFiles)
		        .map(wf -> wf.file)
		        .collect(toUnmodifiableSet());

		final var allLosted = allWatchedFiles.values().stream()
		        .filter(WatchedFile::notExists)
		        .filter(wf -> wf.file.isDirectory() && pickUpDirs
		                      || wf.file.isDirectory() == false && pickUpFiles)
		        .filter(WatchedFile::isNotFounded)
		        .map(wf -> wf.file)
		        .collect(toUnmodifiableSet());

		/**
		 * Update internal list
		 */
		allFounded.stream()
		        .map(allWatchedFiles::get)
		        .forEach(WatchedFile::setFounded);
		/**
		 * Clean deleted files
		 */
		allWatchedFiles.values().stream()
		        .filter(WatchedFile::notExists)
		        .collect(toUnmodifiableList())
		        .forEach(wf -> allWatchedFiles.remove(wf.file));

		int size;
		if (pickUpDirs && pickUpFiles) {
			size = allWatchedFiles.size();
		} else {
			size = (int) allWatchedFiles.values()
			        .stream()
			        .filter(wf -> wf.file.isDirectory() && pickUpDirs
			                      || wf.file.isDirectory() == false && pickUpFiles)
			        .count();
		}

		log.debug("Scan result for {}: {} founded, {} lost, {} total",
		        observedFolder.getLabel(), allFounded.size(), allLosted.size(), size);
		return new WatchedFiles(allFounded, allLosted, size);
	}

	/**
	 * Recursive
	 */
	private void actualScan(final File source, final int deep, final Set<File> detected) {
		final var actualFiles = List.of(source.listFiles());

		final var ignoreFiles = observedFolder.getIgnoreFiles();
		final var allowedHidden = observedFolder.isAllowedHidden();
		final var allowedLinks = observedFolder.isAllowedLinks();
		final var allowedExtentions = observedFolder.getAllowedExtentions();
		final var blockedExtentions = observedFolder.getBlockedExtentions();
		final var ignoreRelativePaths = observedFolder.getIgnoreRelativePaths();

		final var result = actualFiles.stream()
		        .filter(File::canRead)
		        .filter(f -> ignoreFiles.contains(f.getName().toLowerCase()) == false)
		        .filter(f -> (allowedHidden == false && (f.isHidden() || f.getName().startsWith("."))) == false)
		        .filter(f -> (allowedLinks == false && FileUtils.isSymlink(f)) == false)
		        .filter(f -> {
			        if (f.isDirectory()) {
				        return true;
			        } else if (allowedExtentions.isEmpty() == false) {
				        return allowedExtentions.contains(getExtension(f.getName()).toLowerCase());
			        }
			        return true;
		        })
		        .filter(f -> {
			        if (f.isDirectory()) {
				        return true;
			        }
			        return blockedExtentions.contains(getExtension(f.getName()).toLowerCase()) == false;
		        })
		        .filter(f -> {
			        if (ignoreRelativePaths.isEmpty()) {
				        return true;
			        }
			        final var relativePath = f.getAbsolutePath()
			                .substring(rootFolderPathSize + 1)
			                .replace('\\', '/');
			        return ignoreRelativePaths.contains(relativePath) == false;
		        })
		        .filter(f -> {
			        if (f.isDirectory()) {
				        return true;
			        }
			        try {
				        return Optional.ofNullable(Files.readAttributes(source.toPath(), BasicFileAttributes.class))
				                .map(BasicFileAttributes::isOther)
				                .orElse(false) == false;
			        } catch (final IOException e) {
				        log.trace("Can't access to BasicFileAttributes for {}", source, e);
			        }
			        return true;
		        })
		        .collect(Collectors.toUnmodifiableList());

		detected.addAll(result);

		log.trace(() -> "Scanned files/dirs for \"" + source + "\" (deep " + deep + "): "
		                + result.stream()
		                        .map(File::getName)
		                        .sorted()
		                        .collect(Collectors.joining(", ")));
		if (deep > 0) {
			result.stream()
			        .filter(File::isDirectory)
			        .forEach(f -> actualScan(f, deep - 1, detected));
		}
	}

}
