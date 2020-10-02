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

public enum WatchFolderPickupType {

	FILES_ONLY(true, false),
	DIRS_ONLY(false, true),
	FILES_DIRS(true, true);

	private final boolean pickUpFiles;
	private final boolean pickUpDirs;

	private WatchFolderPickupType(final boolean pickUpFiles, final boolean pickUpDirs) {
		this.pickUpFiles = pickUpFiles;
		this.pickUpDirs = pickUpDirs;
	}

	public boolean isPickUpDirs() {
		return pickUpDirs;
	}

	public boolean isPickUpFiles() {
		return pickUpFiles;
	}
}
