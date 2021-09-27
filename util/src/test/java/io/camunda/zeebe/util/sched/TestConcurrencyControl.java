/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.util.sched;

import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.TestActorFuture;
import java.util.function.BiConsumer;

/**
 * Test implementation of {@code ConcurrencyControl}. The main goal is to use this in tests without
 * starting the actor scheduler.
 *
 * <p>The fact that this is used in tests without actor scheduler also means that its behavior
 * differs from a {@code ConcurrencyControl} implementations based on the actor scheduler. The
 * differences are as follows:
 *
 * <ul>
 *   <li>Callables, runaables passed to its methods are called immediately, synchronously on the
 *       current thread (as opposed to the actor scheduler which would schedule them to run deferred
 *       and asynchronous - from the point of view of the caller)
 *   <li>Works best in conjunction with {@code TestActorFuture} returned by this class
 * </ul>
 *
 * Due to these limitations this implementation is ideal for unit tests. <br>
 * However, precaution is deserved to not rely only on unit tests alone. Developers are advised to
 * accompany unit tests with integration/acceptance tests which do use the actor scheduler in order
 * to test the dynamic scheduling behavior.
 */
public class TestConcurrencyControl implements ConcurrencyControl {

  @Override
  public <T> void runOnCompletion(
      final ActorFuture<T> future, final BiConsumer<T, Throwable> callback) {
    future.onComplete(callback);
  }

  @Override
  public void run(final Runnable action) {
    action.run();
  }

  @Override
  public <V> ActorFuture<V> createFuture() {
    return new TestActorFuture<>();
  }

  @Override
  public <V> ActorFuture<V> createCompletedFuture() {
    return completedFuture(null);
  }

  public <U> ActorFuture<U> completedFuture(final U value) {
    final ActorFuture<U> result = createFuture();
    result.complete(value);
    return result;
  }

  public <U> ActorFuture<U> failedFuture(final Throwable error) {
    final ActorFuture<U> result = createFuture();
    result.completeExceptionally(error);
    return result;
  }
}
