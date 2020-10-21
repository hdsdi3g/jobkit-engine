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
import static java.util.stream.Collectors.toUnmodifiableList;
import static java.util.stream.Collectors.toUnmodifiableMap;

import java.time.Duration;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.jobkit.engine.BackgroundService;
import tv.hd3g.jobkit.engine.JobKitEngine;

public class Watchfolders {
	private static final Logger log = LogManager.getLogger();

	private final List<? extends ObservedFolder> observedFolders;
	private final FolderActivity eventActivity;
	private final Duration timeBetweenScans;
	private final JobKitEngine jobKitEngine;
	private final String spoolScans;
	private final String spoolEvents;
	private final Map<ObservedFolder, WatchedFilesDb> wfDBForFolder;
	private final Set<ObservedFolder> missingObservedFolders;

	private BackgroundService service;

	public Watchfolders(final List<? extends ObservedFolder> observedFolders,
	                    final FolderActivity eventActivity,
	                    final Duration timeBetweenScans,
	                    final JobKitEngine jobKitEngine,
	                    final String spoolScans,
	                    final String spoolEvents,
	                    final Supplier<WatchedFilesDb> watchedFilesDbBuilder) {
		this.observedFolders = Objects.requireNonNull(observedFolders);
		this.eventActivity = Objects.requireNonNull(eventActivity);
		this.timeBetweenScans = Objects.requireNonNull(timeBetweenScans);
		this.jobKitEngine = Objects.requireNonNull(jobKitEngine);
		this.spoolScans = Objects.requireNonNull(spoolScans);
		this.spoolEvents = Objects.requireNonNull(spoolEvents);
		missingObservedFolders = Collections.synchronizedSet(new HashSet<>());
		Objects.requireNonNull(watchedFilesDbBuilder);

		if (observedFolders.isEmpty()) {
			log.warn("No configured watchfolders for {}/{}", spoolScans, spoolEvents);
		}

		wfDBForFolder = observedFolders.stream()
		        .collect(toUnmodifiableMap(observedFolder -> observedFolder,
		                observedFolder -> {
			                final var watchedFilesDb = watchedFilesDbBuilder.get();
			                final var pickUp = eventActivity.getPickUpType(observedFolder);
			                watchedFilesDb.setup(observedFolder, pickUp);
			                return watchedFilesDb;
		                }));

		jobKitEngine.runOneShot("Check input directories presence for watchfolders", spoolEvents, 0,
		        () -> {
			        final var list = observedFolders.stream().filter(oF -> {
				        final var aF = oF.getActiveFolder();
				        return aF.exists() == false || aF.isDirectory() == false;
			        }).collect(toUnmodifiableList());

			        if (list.isEmpty() == false) {
				        log.warn("Can't found declared root dir: {}", list);
				        eventActivity.onBootInvalidActiveFolders(list);
				        missingObservedFolders.addAll(list);
			        }
		        }, onOneShotError);
	}

	private final Consumer<Exception> onOneShotError = e -> {
		if (e != null) {
			log.error("Can't send event", e);
		}
	};

	private void internalScan(final ObservedFolder folder) {
		final var activeFolder = folder.getActiveFolder();
		final var label = folder.getLabel();

		if (activeFolder.exists() == false || activeFolder.isDirectory() == false) {
			if (missingObservedFolders.contains(folder) == false) {
				missingObservedFolders.add(folder);
				jobKitEngine.runOneShot("Can't found watchfolder " + label, spoolEvents, 0,
				        () -> eventActivity.onBeforeScanInvalidActiveFolder(folder), onOneShotError);
				log.warn("Can't found watchfolder {} :: {}, cancel scans for it", label, activeFolder);
			}
			return;
		}
		final var getBackWF = missingObservedFolders.remove(folder);
		if (getBackWF) {
			log.info("Get back watchfolder root directory: {} :: {}", label, activeFolder);
		}

		log.trace("Start Watchfolder scan for {} :: {}", label, activeFolder);
		jobKitEngine.runOneShot("Watchfolder start dir scan for " + label, spoolEvents, 0,
		        () -> eventActivity.onBeforeScan(folder), onOneShotError);
		final var startTime = System.currentTimeMillis();
		final var scanResult = wfDBForFolder.get(folder).update();
		final var scanTime = Duration.of(System.currentTimeMillis() - startTime, MILLIS);

		jobKitEngine.runOneShot("On event on watchfolder scan for " + getWFName(), spoolEvents, 0,
		        () -> eventActivity.onAfterScan(folder, scanTime, scanResult), onOneShotError);
		log.trace("Ends Watchfolder scan for {} :: {}", label, activeFolder);
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
			        eventActivity.onStartScans(observedFolders);
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
			        eventActivity.onStopScans(observedFolders);
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
