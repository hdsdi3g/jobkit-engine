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

import java.io.IOException;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import tv.hd3g.commons.IORuntimeException;
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
	private final Map<ObservedFolder, BackgroundService> onErrorObservedFolders;

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
		onErrorObservedFolders = new ConcurrentHashMap<>();
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
	}

	private final Consumer<Exception> onOneShotError = e -> {
		if (e != null) {
			log.error("Can't send event", e);
		}
	};

	private void internalScan(final ObservedFolder folder) {
		try (var fs = folder.createFileSystem()) {
			final var label = folder.getLabel();

			log.trace("Start Watchfolder scan for {} :: {}", label, fs);
			jobKitEngine.runOneShot("Watchfolder start dir scan for " + label, spoolEvents, 0,
			        () -> eventActivity.onBeforeScan(folder), onOneShotError);
			final var startTime = System.currentTimeMillis();
			final var scanResult = wfDBForFolder.get(folder).update(fs);
			final var scanTime = Duration.of(System.currentTimeMillis() - startTime, MILLIS);

			jobKitEngine.runOneShot("On event on watchfolder scan for " + getWFName(), spoolEvents, 0,
			        () -> eventActivity.onAfterScan(folder, scanTime, scanResult), onOneShotError);
			log.trace("Ends Watchfolder scan for {} :: {}", label, fs);
		} catch (final IOException e) {
			throw new IORuntimeException(e);
		}
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

			final var newInError = observedFolders.stream()
			        .filter(Predicate.not(onErrorObservedFolders::containsKey))
			        .filter(oF -> {
				        try {
					        internalScan(oF);
					        return false;
				        } catch (final IORuntimeException e) {
					        log.error("Problem during scan with {}, cancel scans for it", oF.getLabel(), e);
					        jobKitEngine.runOneShot("Problem during scan with watchfolder " + oF.getLabel(),
					                spoolEvents, 0,
					                () -> eventActivity.onScanErrorFolder(oF), onOneShotError);
					        return true;
				        }
			        }).collect(toUnmodifiableList());

			log.trace("Ends full Watchfolders scans for {} ({} ms) - {} in error",
			        getWFName(), System.currentTimeMillis() - startTime, newInError.size());
			retryInError(newInError);
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

	private BackgroundService retryInError(final ObservedFolder newInError) {
		return jobKitEngine.createService("Retry for watchfolders in error " + getWFName(),
		        spoolScans, () -> {
			        final var label = newInError.getLabel();
			        log.info("Retry to establish a connection to {}...", label);
			        try (var fs = newInError.createFileSystem()) {
				        fs.getRootPath().toCachedList().count();
			        } catch (final IOException e) {
				        throw new IORuntimeException(e);
			        }
			        log.info("Connection is ok. Back to normal for {}", label);
			        Optional.ofNullable(onErrorObservedFolders.remove(newInError))
			                .ifPresent(BackgroundService::disable);
		        });
	}

	private void retryInError(final List<? extends ObservedFolder> newInError) {
		newInError.forEach(oF -> {
			final var serviceRetry = retryInError(oF);
			serviceRetry.setTimedInterval(timeBetweenScans);
			serviceRetry.setRetryAfterTimeFactor(10);
			serviceRetry.setPriority(service.getPriority() - 1);
			onErrorObservedFolders.put(oF, service);
			serviceRetry.enable();
		});
	}

}
