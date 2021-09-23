/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.system.partitions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.only;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.atomix.primitive.partition.PartitionId;
import io.atomix.raft.RaftServer.Role;
import io.atomix.raft.partition.RaftPartition;
import io.atomix.raft.partition.impl.RaftPartitionServer;
import io.camunda.zeebe.util.exception.UnrecoverableException;
import io.camunda.zeebe.util.health.CriticalComponentsHealthMonitor;
import io.camunda.zeebe.util.health.FailureListener;
import io.camunda.zeebe.util.health.HealthStatus;
import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;
import io.camunda.zeebe.util.sched.future.CompletableActorFuture;
import io.camunda.zeebe.util.sched.testing.ControlledActorSchedulerRule;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.InOrder;

public class ZeebePartitionTest {

  @Rule public ControlledActorSchedulerRule schedulerRule = new ControlledActorSchedulerRule();

  private PartitionStartupAndTransitionContextImpl ctx;
  private PartitionTransition transition;
  private CriticalComponentsHealthMonitor healthMonitor;
  private RaftPartition raft;
  private ZeebePartition partition;

  @Before
  public void setup() {
    ctx = mock(PartitionStartupAndTransitionContextImpl.class);
    transition = spy(new NoopTransition());

    raft = mock(RaftPartition.class);
    when(raft.id()).thenReturn(new PartitionId("", 0));
    when(raft.getRole()).thenReturn(Role.INACTIVE);
    when(raft.getServer()).thenReturn(mock(RaftPartitionServer.class));

    healthMonitor = mock(CriticalComponentsHealthMonitor.class);

    when(ctx.getRaftPartition()).thenReturn(raft);
    when(ctx.getPartitionContext()).thenReturn(ctx);
    when(ctx.getComponentHealthMonitor()).thenReturn(healthMonitor);
    when(ctx.createTransitionContext()).thenReturn(ctx);

    partition = new ZeebePartition(ctx, transition, List.of(new NoopStartupStep()));
  }

  @Test
  public void shouldInstallLeaderPartition() {
    // given
    schedulerRule.submitActor(partition);

    // when
    partition.onNewRole(Role.LEADER, 1);
    schedulerRule.workUntilDone();

    // then
    verify(transition).toLeader(1);
  }

  @Test
  public void shouldCallOnFailureOnAddFailureListenerAndUnhealthy() {
    // given
    when(healthMonitor.getHealthStatus()).thenReturn(HealthStatus.UNHEALTHY);
    final FailureListener failureListener = mock(FailureListener.class);
    doNothing().when(failureListener).onFailure();
    schedulerRule.submitActor(partition);

    // when
    partition.addFailureListener(failureListener);
    schedulerRule.workUntilDone();

    // then
    verify(failureListener, only()).onFailure();
  }

  @Test
  public void shouldCallOnRecoveredOnAddFailureListenerAndHealthy() {
    // given
    final FailureListener failureListener = mock(FailureListener.class);
    doNothing().when(failureListener).onRecovered();

    // make partition healthy
    when(healthMonitor.getHealthStatus()).thenReturn(HealthStatus.HEALTHY);
    schedulerRule.submitActor(partition);
    schedulerRule.workUntilDone();

    // when
    partition.addFailureListener(failureListener);
    schedulerRule.workUntilDone();

    // then
    verify(failureListener, only()).onRecovered();
  }

  @Test
  public void shouldStepDownAfterFailedLeaderTransition() throws InterruptedException {
    // given
    final CountDownLatch latch = new CountDownLatch(1);

    when(transition.toLeader(anyLong()))
        .thenReturn(CompletableActorFuture.completedExceptionally(new Exception("expected")));
    when(transition.toFollower(anyLong()))
        .then(
            invocation -> {
              latch.countDown();
              return CompletableActorFuture.completed(null);
            });
    when(raft.getRole()).thenReturn(Role.LEADER);
    when(raft.term()).thenReturn(1L);
    when(ctx.getCurrentRole()).thenReturn(Role.LEADER);
    when(ctx.getCurrentTerm()).thenReturn(1L);
    when(raft.stepDown())
        .then(
            invocation -> {
              partition.onNewRole(Role.FOLLOWER, 1);
              return CompletableFuture.completedFuture(null);
            });

    // when
    schedulerRule.submitActor(partition);
    partition.onNewRole(Role.LEADER, 1);
    schedulerRule.workUntilDone();
    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

    // then
    final InOrder order = inOrder(transition, raft);
    order.verify(transition).toLeader(1);
    order.verify(raft).stepDown();
    order.verify(transition).toFollower(1);
  }

  @Test
  public void shouldGoInactiveAfterFailedFollowerTransition() throws InterruptedException {
    // given
    final CountDownLatch latch = new CountDownLatch(1);

    when(transition.toFollower(anyLong()))
        .thenReturn(CompletableActorFuture.completedExceptionally(new Exception("expected")));
    when(transition.toInactive())
        .then(
            invocation -> {
              latch.countDown();
              return CompletableActorFuture.completed(null);
            });
    when(raft.getRole()).thenReturn(Role.FOLLOWER);
    when(ctx.getCurrentRole()).thenReturn(Role.FOLLOWER);
    when(raft.goInactive())
        .then(
            invocation -> {
              partition.onNewRole(Role.INACTIVE, 2);
              return CompletableFuture.completedFuture(null);
            });

    // when
    schedulerRule.submitActor(partition);
    schedulerRule.workUntilDone();
    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

    // then
    final InOrder order = inOrder(transition, raft);
    order.verify(transition).toFollower(0L);
    order.verify(raft).goInactive();
    order.verify(transition).toInactive();
  }

  @Test
  public void shouldGoInactiveIfTransitionHasUnrecoverableFailure() throws InterruptedException {
    // given
    final CountDownLatch latch = new CountDownLatch(1);
    when(transition.toLeader(anyLong()))
        .thenReturn(
            CompletableActorFuture.completedExceptionally(new UnrecoverableException("expected")));
    when(transition.toInactive())
        .then(
            invocation -> {
              latch.countDown();
              return CompletableActorFuture.completed(null);
            });
    when(raft.getRole()).thenReturn(Role.LEADER);

    // when
    schedulerRule.submitActor(partition);
    schedulerRule.workUntilDone();
    assertThat(latch.await(30, TimeUnit.SECONDS)).isTrue();

    // then
    final InOrder order = inOrder(transition, raft);
    order.verify(transition).toLeader(0L);
    order.verify(transition).toInactive();
    order.verify(raft).goInactive();
  }

  private static class NoopTransition implements PartitionTransition {

    @Override
    public ActorFuture<Void> toFollower(final long currentTerm) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> toLeader(final long currentTerm) {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public ActorFuture<Void> toInactive() {
      return CompletableActorFuture.completed(null);
    }

    @Override
    public void setConcurrencyControl(final ConcurrencyControl concurrencyControl) {
      // Do nothing
    }

    @Override
    public void updateTransitionContext(final PartitionTransitionContext transitionContext) {
      // Do nothing
    }
  }

  private static class NoopStartupStep implements PartitionStartupStep {

    @Override
    public String getName() {
      return "noop";
    }

    @Override
    public ActorFuture<PartitionStartupContext> startup(
        final PartitionStartupContext partitionStartupContext) {
      return CompletableActorFuture.completed(partitionStartupContext);
    }

    @Override
    public ActorFuture<PartitionStartupContext> shutdown(
        final PartitionStartupContext partitionStartupContext) {
      return CompletableActorFuture.completed(partitionStartupContext);
    }
  }
}
