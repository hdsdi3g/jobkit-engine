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

import java.util.Set;

import tv.hd3g.transfertfiles.CachedFileAttributes;

public class WatchedFiles {

	private final Set<CachedFileAttributes> founded;
	private final Set<CachedFileAttributes> losted;
	private final int totalFiles;

	/**
	 * @param founded file/dir added on scanned folder
	 * @param losted file/dir removed before validation on scanned folder
	 * @return totalFiles total file/dir count on scanned folder (valided)
	 */
	public WatchedFiles(final Set<CachedFileAttributes> founded,
	                    final Set<CachedFileAttributes> losted,
	                    final int totalFiles) {
		this.founded = founded;
		this.losted = losted;
		this.totalFiles = totalFiles;
	}

	public Set<CachedFileAttributes> getFounded() {
		return founded;
	}

	public Set<CachedFileAttributes> getLosted() {
		return losted;
	}

	public int getTotalFiles() {
		return totalFiles;
	}

}
