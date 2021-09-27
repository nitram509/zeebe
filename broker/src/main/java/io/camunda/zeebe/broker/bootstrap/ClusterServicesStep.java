/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.util.sched.ConcurrencyControl;
import io.camunda.zeebe.util.sched.future.ActorFuture;

class ClusterServicesStep extends AbstractBrokerStartupStep {

  @Override
  public String getName() {
    return "Cluster Services (Start)";
  }

  @Override
  void startupInternal(
      final BrokerStartupContext brokerStartupContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> startupFuture) {

    final var clusterServices = brokerStartupContext.getClusterServices();

    clusterServices
        .start()
        .whenComplete(
            (ok, error) -> {
              if (error != null) {
                startupFuture.completeExceptionally(error);
              } else {
                startupFuture.complete(brokerStartupContext);
              }
            });
  }

  @Override
  void shutdownInternal(
      final BrokerStartupContext brokerShutdownContext,
      final ConcurrencyControl concurrencyControl,
      final ActorFuture<BrokerStartupContext> shutdownFuture) {

    final var clusterServices = brokerShutdownContext.getClusterServices();

    clusterServices
        .stop()
        .whenComplete(
            (ok, error) -> {
              if (error != null) {
                shutdownFuture.completeExceptionally(error);
              } else {
                shutdownFuture.complete(brokerShutdownContext);
              }
            });
  }
}
