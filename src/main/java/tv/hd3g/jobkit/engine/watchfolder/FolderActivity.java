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
import java.util.List;

public interface FolderActivity {

	default void onStartScans(final List<? extends ObservedFolder> observedFolder) {
	}

	default void onStopScans(final List<? extends ObservedFolder> observedFolder) {
	}

	default void onBeforeScan(final ObservedFolder observedFolder) {
	}

	default WatchFolderPickupType getPickUpType(final ObservedFolder observedFolder) {
		return WatchFolderPickupType.FILES_ONLY;
	}

	void onAfterScan(final ObservedFolder observedFolder,
	                 final Duration scanTime,
	                 final WatchedFiles scanResult);

	default void onBootInvalidActiveFolders(final List<? extends ObservedFolder> observedFolder) {
	}

	default void onBeforeScanInvalidActiveFolder(final ObservedFolder observedFolder) {
	}

}
