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

import static java.util.stream.Collectors.joining;
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableSet;
import static org.apache.commons.io.FilenameUtils.getExtension;
import static tv.hd3g.jobkit.engine.watchfolder.WatchFolderPickupType.FILES_DIRS;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.commons.IORuntimeException;
import tv.hd3g.transfertfiles.AbstractFile;
import tv.hd3g.transfertfiles.AbstractFileSystemURL;
import tv.hd3g.transfertfiles.CachedFileAttributes;

/**
 * Not thread safe
 */
public class WatchedFilesInMemoryDb implements WatchedFilesDb {
	private static final Logger log = LogManager.getLogger();

	private final Map<CachedFileAttributes, WatchedFileInMemoryDb> allWatchedFiles;

	private int maxDeep;
	private ObservedFolder observedFolder;
	private WatchFolderPickupType pickUp;
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
		this.pickUp = pickUp;
		minFixedStateTime = observedFolder.getMinFixedStateTime();
		try (var fs = observedFolder.createFileSystem()) {
			/** try only to load FileSystem/configured URL */
		} catch (final IOException e) {
			throw new IORuntimeException("Can't load FileSystem", e);
		}
		if (observedFolder.isRecursive() == false) {
			maxDeep = 0;
		}
		log.debug("Setup WFDB for {}, pickUp: {}, minFixedStateTime: {}, maxDeep: {}",
		        observedFolder.getLabel(), pickUp, minFixedStateTime, maxDeep);
	}

	@Override
	public WatchedFiles update(final AbstractFileSystemURL fileSystem) {
		final var detected = new HashSet<CachedFileAttributes>();
		actualScan(fileSystem.getRootPath(), maxDeep, detected);

		/**
		 * update all founded
		 */
		final var updateFounded = detected.stream()
		        .filter(allWatchedFiles::containsKey)
		        .map(f -> allWatchedFiles.get(f).update(f))
		        .collect(toUnmodifiableList());
		log.trace("List updateFounded={}", updateFounded);

		/**
		 * get qualified, set them marked
		 */
		final var qualifyFounded = updateFounded.stream()
		        .filter(WatchedFileInMemoryDb::isNotYetMarkedAsDone)
		        .filter(WatchedFileInMemoryDb::isQualified)
		        .map(WatchedFileInMemoryDb::setMarkedAsDone)
		        .collect(toUnmodifiableList());
		log.trace("List qualifyFounded={}", qualifyFounded);

		/**
		 * get only them can be callbacked
		 */
		final var qualifiedAndCallbacked = qualifyFounded.stream()
		        .filter(WatchedFileInMemoryDb::canBeCallbacked)
		        .map(WatchedFileInMemoryDb::getLastFile)
		        .collect(toUnmodifiableSet());
		log.trace("List qualifiedAndCallbacked={}", qualifiedAndCallbacked);

		final var losted = allWatchedFiles.values().stream()
		        .filter(WatchedFileInMemoryDb::isNotYetMarkedAsDone)
		        .filter(w -> w.absentInSet(detected))
		        .collect(toUnmodifiableList());

		final var lostedAndCallbacked = losted.stream()
		        .filter(WatchedFileInMemoryDb::canBePickup)
		        .map(WatchedFileInMemoryDb::getLastFile)
		        .collect(toUnmodifiableSet());

		/**
		 * Add new files
		 */
		detected.stream()
		        .filter(Predicate.not(allWatchedFiles::containsKey))
		        .forEach(f -> allWatchedFiles.put(f, new WatchedFileInMemoryDb(f, pickUp, minFixedStateTime)));

		/**
		 * Clean deleted files
		 */
		allWatchedFiles.keySet().stream()
		        .filter(Predicate.not(detected::contains))
		        .collect(toUnmodifiableList())
		        .forEach(allWatchedFiles::remove);

		int size;
		if (pickUp == FILES_DIRS) {
			size = allWatchedFiles.size();
		} else {
			size = (int) allWatchedFiles.values().stream()
			        .filter(WatchedFileInMemoryDb::canBePickup)
			        .count();
		}

		log.debug("Scan result for {}: {} founded, {} lost, {} total",
		        observedFolder.getLabel(), qualifiedAndCallbacked.size(), lostedAndCallbacked.size(), size);
		return new WatchedFiles(qualifiedAndCallbacked, lostedAndCallbacked, size);
	}

	/**
	 * Recursive
	 */
	private void actualScan(final AbstractFile aSource,
	                        final int deep,
	                        final Set<CachedFileAttributes> detected) {
		final var ignoreFiles = observedFolder.getIgnoreFiles();
		final var allowedHidden = observedFolder.isAllowedHidden();
		final var allowedLinks = observedFolder.isAllowedLinks();
		final var allowedExtentions = observedFolder.getAllowedExtentions();
		final var blockedExtentions = observedFolder.getBlockedExtentions();
		final var ignoreRelativePaths = observedFolder.getIgnoreRelativePaths();

		final var result = aSource.toCachedList()
		        .peek(f -> log.trace("Detect file={}", f))// NOSONAR S3864
		        .filter(f -> ignoreFiles.contains(f.getName().toLowerCase()) == false)
		        .filter(f -> (allowedHidden == false && (f.isHidden() || f.getName().startsWith("."))) == false)
		        .filter(f -> (allowedLinks == false && f.isLink()) == false)
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
			        return ignoreRelativePaths.contains(f.getPath()) == false;
		        })
		        .filter(f -> f.isDirectory() || f.isSpecial() == false)
		        .collect(toUnmodifiableList());

		detected.addAll(result);

		log.debug(() -> "Scanned files/dirs for \"" + aSource.getPath() + "\" (deep " + deep + "): "
		                + result.stream()
		                        .map(CachedFileAttributes::getName)
		                        .sorted()
		                        .collect(joining(", "))
		                + " on \"" + aSource.getFileSystem().toString() + "\"");
		if (deep > 0) {
			result.stream()
			        .filter(CachedFileAttributes::isDirectory)
			        .forEach(f -> actualScan(f.getAbstractFile(), deep - 1, detected));
		}
	}

}
