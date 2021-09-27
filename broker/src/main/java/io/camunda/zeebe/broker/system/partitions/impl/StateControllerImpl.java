/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions.impl;

import io.camunda.zeebe.broker.system.partitions.AtomixRecordEntrySupplier;
import io.camunda.zeebe.broker.system.partitions.StateController;
import io.camunda.zeebe.db.ZeebeDb;
import io.camunda.zeebe.db.ZeebeDbFactory;
import io.camunda.zeebe.logstreams.impl.Loggers;
import io.camunda.zeebe.snapshots.ConstructableSnapshotStore;
import io.camunda.zeebe.snapshots.TransientSnapshot;
import io.camunda.zeebe.util.FileUtil;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.function.ToLongFunction;
import org.slf4j.Logger;

/** Controls how snapshot/recovery operations are performed */
public class StateControllerImpl implements StateController {

  private static final Logger LOG = Loggers.SNAPSHOT_LOGGER;

  private final Path runtimeDirectory;

  @SuppressWarnings("rawtypes")
  private final ZeebeDbFactory zeebeDbFactory;

  @SuppressWarnings("rawtypes")
  private final ToLongFunction<ZeebeDb> exporterPositionSupplier;

  private final AtomixRecordEntrySupplier entrySupplier;

  @SuppressWarnings("rawtypes")
  private ZeebeDb db;

  private final ConstructableSnapshotStore constructableSnapshotStore;
  private final ConcurrencyControl concurrencyControl;

  public StateControllerImpl(
      @SuppressWarnings("rawtypes") final ZeebeDbFactory zeebeDbFactory,
      final ConstructableSnapshotStore constructableSnapshotStore,
      final Path runtimeDirectory,
      final AtomixRecordEntrySupplier entrySupplier,
      @SuppressWarnings("rawtypes") final ToLongFunction<ZeebeDb> exporterPositionSupplier,
      final ConcurrencyControl concurrencyControl) {
    this.constructableSnapshotStore = constructableSnapshotStore;
    this.runtimeDirectory = runtimeDirectory;
    this.zeebeDbFactory = zeebeDbFactory;
    this.exporterPositionSupplier = exporterPositionSupplier;
    this.entrySupplier = entrySupplier;
    this.concurrencyControl = concurrencyControl;
  }

  @Override
  public ActorFuture<Optional<TransientSnapshot>> takeTransientSnapshot(
      final long lowerBoundSnapshotPosition) {
    final ActorFuture<Optional<TransientSnapshot>> future = concurrencyControl.createFuture();
    concurrencyControl.run(() -> takeTransientSnapshotInternal(lowerBoundSnapshotPosition, future));
    return future;
  }

  @Override
  public ActorFuture<ZeebeDb> recover() {
    final ActorFuture<ZeebeDb> future = concurrencyControl.createFuture();
    concurrencyControl.run(() -> recoverInternal(future));
    return future;
  }

  @Override
  public ActorFuture<Void> closeDb() {
    final ActorFuture<Void> future = concurrencyControl.createFuture();
    concurrencyControl.run(() -> closeDbInternal(future));
    return future;
  }

  private void closeDbInternal(final ActorFuture<Void> future) {
    try {
      if (db != null) {
        final var dbToClose = db;
        db = null;
        dbToClose.close();

        LOG.debug("Closed database from '{}'.", runtimeDirectory);
      }

      tryDeletingRuntimeDirectory();
      future.complete(null);
    } catch (final Exception e) {
      future.completeExceptionally(e);
    }
  }

  private void recoverInternal(final ActorFuture<ZeebeDb> future) {
    try {
      FileUtil.deleteFolderIfExists(runtimeDirectory);
    } catch (final IOException e) {
      future.completeExceptionally(
          new RuntimeException(
              "Failed to delete runtime folder. Cannot recover from snapshot.", e));
    }

    final var optLatestSnapshot = constructableSnapshotStore.getLatestSnapshot();
    if (optLatestSnapshot.isPresent()) {
      final var snapshot = optLatestSnapshot.get();
      LOG.debug("Recovering state from available snapshot: {}", snapshot);
      constructableSnapshotStore
          .copySnapshot(snapshot, runtimeDirectory)
          .onComplete(
              (ok, error) -> {
                if (error != null) {
                  future.completeExceptionally(
                      new RuntimeException(
                          String.format("Failed to recover from snapshot %s", snapshot.getId()),
                          error));
                } else {
                  openDb(future);
                }
              });
    } else {
      // If there is no snapshot, open empty database
      openDb(future);
    }
  }

  private void takeTransientSnapshotInternal(
      final long lowerBoundSnapshotPosition,
      final ActorFuture<Optional<TransientSnapshot>> future) {
    if (!isDbOpened()) {
      LOG.warn(
          "Expected to take snapshot for last processed position {}, but database was closed.",
          lowerBoundSnapshotPosition);
      future.complete(Optional.empty());
      return;
    }

    final long exportedPosition = exporterPositionSupplier.applyAsLong(db);
    final long snapshotPosition =
        determineSnapshotPosition(lowerBoundSnapshotPosition, exportedPosition);
    final var optionalIndexed = entrySupplier.getPreviousIndexedEntry(snapshotPosition);
    if (optionalIndexed.isEmpty()) {
      future.completeExceptionally(
          new IllegalStateException(
              String.format(
                  "Failed to take snapshot. Expected to find an indexed entry for determined snapshot position %d (processedPosition = %d, exportedPosition=%d), but found no matching indexed entry which contains this position.",
                  snapshotPosition, lowerBoundSnapshotPosition, exportedPosition)));
      return;
    }

    final var snapshotIndexedEntry = optionalIndexed.get();
    final Optional<TransientSnapshot> transientSnapshot =
        constructableSnapshotStore.newTransientSnapshot(
            snapshotIndexedEntry.index(),
            snapshotIndexedEntry.term(),
            lowerBoundSnapshotPosition,
            exportedPosition);

    // Now takeSnapshot result can be either true, false or error.
    transientSnapshot.ifPresentOrElse(
        snapshot -> takeSnapshot(snapshot, future), () -> future.complete(Optional.empty()));
  }

  @SuppressWarnings("rawtypes")
  private void openDb(final ActorFuture<ZeebeDb> future) {
    try {
      if (db == null) {
        db = zeebeDbFactory.createDb(runtimeDirectory.toFile());
        LOG.debug("Opened database from '{}'.", runtimeDirectory);
        future.complete(db);
      }
    } catch (final Exception error) {
      future.completeExceptionally(new RuntimeException("Failed to open database", error));
    }
  }

  private void tryDeletingRuntimeDirectory() {
    try {
      FileUtil.deleteFolderIfExists(runtimeDirectory);
    } catch (final Exception e) {
      LOG.debug("Failed to delete runtime directory when closing", e);
    }
  }

  @Override
  public void close() throws Exception {
    closeDb();
  }

  boolean isDbOpened() {
    return db != null;
  }

  private void takeSnapshot(
      final TransientSnapshot snapshot,
      final ActorFuture<Optional<TransientSnapshot>> transientSnapshotFuture) {
    final var snapshotTaken =
        snapshot.take(
            snapshotDir -> {
              if (db == null) {
                LOG.error("Expected to take a snapshot, but no database was opened");
                return false;
              }

              LOG.debug("Taking temporary snapshot into {}.", snapshotDir);
              try {
                db.createSnapshot(snapshotDir.toFile());
              } catch (final Exception e) {
                LOG.error("Failed to create snapshot of runtime database", e);
                return false;
              }

              return true;
            });

    // TODO: Remove boolean response, and always throw error when snapshot was not taken.
    snapshotTaken.onComplete(
        (taken, error) -> {
          if (error != null) {
            transientSnapshotFuture.completeExceptionally(error);
          } else if (taken) {
            transientSnapshotFuture.complete(Optional.of(snapshot));
          } else {
            transientSnapshotFuture.complete(Optional.empty());
          }
        });
  }

  private long determineSnapshotPosition(
      final long lowerBoundSnapshotPosition, final long exportedPosition) {
    final long snapshotPosition = Math.min(exportedPosition, lowerBoundSnapshotPosition);
    LOG.trace(
        "Based on lowest exporter position '{}' and last processed position '{}', determined '{}' as snapshot position.",
        exportedPosition,
        lowerBoundSnapshotPosition,
        snapshotPosition);
    return snapshotPosition;
  }
}
