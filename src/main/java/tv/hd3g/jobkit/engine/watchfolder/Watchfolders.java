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

import static java.time.temporal.ChronoUnit.MILLIS;
import static java.util.stream.Collectors.toUnmodifiableMap;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.jobkit.engine.BackgroundService;
import tv.hd3g.jobkit.engine.JobKitEngine;

public class Watchfolders {
	private static final Logger log = LogManager.getLogger();

	private final List<ObservedFolder> observedFolders;
	private final FolderActivity activity;
	private final Duration timeBetweenScans;
	private final JobKitEngine jobKitEngine;
	private final String spoolScans;
	private final String spoolEvents;
	private final Map<ObservedFolder, WatchedFilesDb> wfDBForFolder;

	private BackgroundService service;

	public Watchfolders(final List<ObservedFolder> observedFolders,
	                    final FolderActivity activity,
	                    final Duration timeBetweenScans,
	                    final JobKitEngine jobKitEngine,
	                    final String spoolScans,
	                    final String spoolEvents,
	                    final Supplier<WatchedFilesDb> watchedFilesDbBuilder) {
		this.observedFolders = Objects.requireNonNull(observedFolders);
		this.activity = Objects.requireNonNull(activity);
		this.timeBetweenScans = Objects.requireNonNull(timeBetweenScans);
		this.jobKitEngine = Objects.requireNonNull(jobKitEngine);
		this.spoolScans = Objects.requireNonNull(spoolScans);
		this.spoolEvents = Objects.requireNonNull(spoolEvents);
		Objects.requireNonNull(watchedFilesDbBuilder);

		if (observedFolders.isEmpty()) {
			log.warn("No configured watchfolders for {}/{}", spoolScans, spoolEvents);
		}
		wfDBForFolder = observedFolders.stream()
		        .collect(toUnmodifiableMap(observedFolder -> observedFolder,
		                observedFolder -> {
			                final var watchedFilesDb = watchedFilesDbBuilder.get();
			                final var pickUp = activity.getPickUpType(observedFolder);
			                watchedFilesDb.setup(observedFolder, pickUp);
			                return watchedFilesDb;
		                }));
	}

	private final Consumer<Exception> onOneShotError = e -> {
		if (e != null) {
			log.error("Can't send event", e);
		}
	};

	private void internalScan(final ObservedFolder folder) {
		log.trace("Start Watchfolder scan for {} :: {}", folder.getLabel(), folder.getActiveFolder());
		jobKitEngine.runOneShot("Watchfolder start dir scan for " + folder.getLabel(), spoolEvents, 0,
		        () -> activity.onBeforeScan(folder), onOneShotError);
		final var startTime = System.currentTimeMillis();
		final var scanResult = wfDBForFolder.get(folder).update();
		final var scanTime = Duration.of(System.currentTimeMillis() - startTime, MILLIS);

		jobKitEngine.runOneShot("On event on watchfolder scan for " + getWFName(), spoolEvents, 0,
		        () -> activity.onAfterScan(folder, scanTime, scanResult), onOneShotError);
		log.trace("Ends Watchfolder scan for {} :: {}", folder.getLabel(), folder.getActiveFolder());
	}

	private String getWFName() {
		return observedFolders.stream()
		        .map(ObservedFolder::getLabel)
		        .collect(Collectors.joining(", "));
	}

	public synchronized void startScans() {
		if (service != null && service.isEnabled()) {
			return;
		}
		service = jobKitEngine.createService("Watchfolder for " + getWFName(), spoolScans, () -> {
			log.trace("Start full Watchfolders scans for {}", getWFName());
			final var startTime = System.currentTimeMillis();
			observedFolders.forEach(this::internalScan);
			log.trace("Ends full Watchfolders scans for {} ({} ms)",
			        getWFName(), System.currentTimeMillis() - startTime);
		});
		service.setTimedInterval(timeBetweenScans);
		service.setRetryAfterTimeFactor(10);
		service.setPriority(0);
		jobKitEngine.runOneShot("Start watchfolder scans for " + getWFName(), spoolEvents, 0,
		        () -> {
			        activity.onStartScans(observedFolders);
			        service.enable();
		        }, onOneShotError);
	}

	/**
	 * Start/stop events from here don't tigger FolderActivity.onStartScans / onStopScans
	 */
	public synchronized BackgroundService getService() {
		return service;
	}

	public synchronized void stopScans() {
		if (service == null || service.isEnabled() == false) {
			return;
		}
		jobKitEngine.runOneShot("Stop watchfolder scans for " + getWFName(), spoolEvents, 0,
		        () -> {
			        service.disable();
			        activity.onStopScans(observedFolders);
		        },
		        e -> {
			        if (e != null) {
				        log.error("Can't send onStopScans event", e);
			        }
			        service.disable();
		        });
		service = null;
	}

}
