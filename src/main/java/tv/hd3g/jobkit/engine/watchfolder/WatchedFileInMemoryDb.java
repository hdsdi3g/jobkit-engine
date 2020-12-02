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

import java.time.Duration;
import java.util.Set;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.transfertfiles.CachedFileAttributes;

class WatchedFileInMemoryDb {
	private static final Logger log = LogManager.getLogger();

	private final boolean isDirectory;
	private final boolean pickUpFiles;
	private final boolean pickUpDirs;
	private final Duration minFixedStateTime;

	private CachedFileAttributes lastFile;
	private long lastWatched;
	private boolean markedAsDone;
	private boolean lastIsSame;

	WatchedFileInMemoryDb(final CachedFileAttributes firstDetectionFile,
	                      final WatchFolderPickupType pickUp,
	                      final Duration minFixedStateTime) {
		lastFile = firstDetectionFile;
		isDirectory = firstDetectionFile.isDirectory();
		lastWatched = System.currentTimeMillis();
		lastIsSame = false;
		pickUpFiles = pickUp.isPickUpFiles();
		pickUpDirs = pickUp.isPickUpDirs();
		this.minFixedStateTime = minFixedStateTime;
		log.trace("Create WatchedFileInMemoryDb for {}, {} ({})",
		        firstDetectionFile, pickUp, minFixedStateTime);
	}

	@Override
	public String toString() {
		final var sb = new StringBuilder();
		sb.append(lastFile);
		sb.append("{since ");
		sb.append(System.currentTimeMillis() - lastWatched);
		sb.append("ms ");
		if (markedAsDone) {
			sb.append("markedAsDone ");
		}
		if (lastIsSame) {
			sb.append("lastIsSame");
		}
		return sb.toString().trim() + "}";
	}

	WatchedFileInMemoryDb update(final CachedFileAttributes seeAgainFile) {
		if (markedAsDone) {
			return this;
		} else if (isDirectory) {
			lastFile = seeAgainFile;
		} else {
			lastIsSame = lastFile.lastModified() == seeAgainFile.lastModified()
			             && lastFile.length() == seeAgainFile.length();
			if (lastIsSame == false) {
				lastWatched = System.currentTimeMillis();
			}
			lastFile = seeAgainFile;
		}
		return this;
	}

	boolean isQualified() {
		final var notTooRecent = lastWatched < System.currentTimeMillis() - minFixedStateTime.toMillis();
		return isDirectory
		       || lastIsSame && notTooRecent;
	}

	boolean canBeCallbacked() {
		return isQualified() && canBePickup();
	}

	boolean canBePickup() {
		return isDirectory && pickUpDirs == true || isDirectory == false && pickUpFiles == true;
	}

	WatchedFileInMemoryDb setMarkedAsDone() {
		markedAsDone = true;
		return this;
	}

	boolean isNotYetMarkedAsDone() {
		return markedAsDone == false;
	}

	CachedFileAttributes getLastFile() {
		return lastFile;
	}

	boolean absentInSet(final Set<CachedFileAttributes> detected) {
		return detected.contains(lastFile) == false;
	}
}
