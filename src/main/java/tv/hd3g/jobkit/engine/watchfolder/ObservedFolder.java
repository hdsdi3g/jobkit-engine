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

import static java.util.stream.Collectors.toUnmodifiableSet;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

import tv.hd3g.commons.IORuntimeException;

public class ObservedFolder {

	private File activeFolder;
	private String label;
	private Set<String> allowedExtentions;
	private Set<String> blockedExtentions;
	private Set<String> ignoreRelativePaths;
	private Set<String> ignoreFiles;
	private boolean allowedHidden;
	private boolean allowedLinks;
	private boolean recursive;
	private Duration minFixedStateTime;

	private static final UnaryOperator<String> removeFirstDot = ext -> {
		if (ext.startsWith(".")) {
			return ext.substring(1);
		}
		return ext;
	};

	void postConfiguration() {
		Objects.requireNonNull(activeFolder, "Null active folder");
		if (activeFolder.exists() == false) {
			throw new IORuntimeException("Can't found \"" + activeFolder + "\"");
		} else if (activeFolder.isDirectory() == false) {
			throw new IORuntimeException("Entry is not a Directory: \"" + activeFolder + "\"");
		} else if (activeFolder.canRead() == false) {
			throw new IORuntimeException("Can't read: \"" + activeFolder + "\"");
		}
		try {
			activeFolder = activeFolder.getCanonicalFile().getAbsoluteFile();
		} catch (final IOException e) {
			throw new IORuntimeException(e);
		}

		if (label == null || label.isEmpty()) {
			label = activeFolder.getName();
		}

		allowedExtentions = Optional.ofNullable(allowedExtentions).orElse(Set.of()).stream()
		        .map(removeFirstDot)
		        .map(String::toLowerCase)
		        .distinct()
		        .collect(toUnmodifiableSet());
		blockedExtentions = Optional.ofNullable(blockedExtentions).orElse(Set.of()).stream()
		        .map(removeFirstDot)
		        .map(String::toLowerCase)
		        .distinct()
		        .collect(toUnmodifiableSet());
		ignoreRelativePaths = Optional.ofNullable(ignoreRelativePaths).orElse(Set.of()).stream()
		        .map(path -> {
			        if (path.startsWith("/") || path.startsWith("\\")) {
				        return path.substring(1).replace('\\', '/');
			        }
			        return path.replace('\\', '/');
		        })
		        .distinct()
		        .collect(toUnmodifiableSet());

		ignoreFiles = Optional.ofNullable(ignoreFiles).orElse(Set.of()).stream()
		        .map(String::toLowerCase)
		        .distinct()
		        .collect(toUnmodifiableSet());
		minFixedStateTime = Optional.ofNullable(minFixedStateTime).orElse(Duration.ZERO);
	}

	public void setLabel(final String label) {
		this.label = label;
	}

	public String getLabel() {
		return label;
	}

	public File getActiveFolder() {
		return activeFolder;
	}

	public void setActiveFolder(final File activeFolder) {
		this.activeFolder = activeFolder;
	}

	public Set<String> getAllowedExtentions() {
		return allowedExtentions;
	}

	public void setAllowedExtentions(final Set<String> allowedExtentions) {
		this.allowedExtentions = allowedExtentions;
	}

	public Set<String> getBlockedExtentions() {
		return blockedExtentions;
	}

	public void setBlockedExtentions(final Set<String> blockedExtentions) {
		this.blockedExtentions = blockedExtentions;
	}

	public Set<String> getIgnoreRelativePaths() {
		return ignoreRelativePaths;
	}

	public void setIgnoreRelativePaths(final Set<String> ignoreRelativePaths) {
		this.ignoreRelativePaths = ignoreRelativePaths;
	}

	public boolean isAllowedHidden() {
		return allowedHidden;
	}

	public void setAllowedHidden(final boolean allowedHidden) {
		this.allowedHidden = allowedHidden;
	}

	public boolean isAllowedLinks() {
		return allowedLinks;
	}

	public void setAllowedLinks(final boolean allowedLinks) {
		this.allowedLinks = allowedLinks;
	}

	public boolean isRecursive() {
		return recursive;
	}

	public void setRecursive(final boolean recursive) {
		this.recursive = recursive;
	}

	public Set<String> getIgnoreFiles() {
		return ignoreFiles;
	}

	public void setIgnoreFiles(final Set<String> ignoreFiles) {
		this.ignoreFiles = ignoreFiles;
	}

	public Duration getMinFixedStateTime() {
		return minFixedStateTime;
	}

	public void setMinFixedStateTime(final Duration minFixedStateTime) {
		this.minFixedStateTime = minFixedStateTime;
	}
}
