/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.broker.bootstrap;

import io.camunda.zeebe.broker.clustering.ClusterServicesImpl;
import io.camunda.zeebe.broker.partitioning.PartitionManager;
import io.camunda.zeebe.broker.system.EmbeddedGatewayService;
import io.camunda.zeebe.broker.system.management.BrokerAdminService;
import io.camunda.zeebe.broker.system.monitoring.DiskSpaceUsageMonitor;

/** Context for components/actors managed directly by the Broker */
public interface BrokerContext {

  // TODO change this to ClusterServices after migration
  ClusterServicesImpl getClusterServices();

  EmbeddedGatewayService getEmbeddedGatewayService();

  /**
   * Returns disk space usage monitor. May be {@code null} if disabled in configuration
   *
   * @return disk space usage monitor. May be {@code null} if disabled in configuration
   */
  DiskSpaceUsageMonitor getDiskSpaceUsageMonitor();

  PartitionManager getPartitionManager();

  BrokerAdminService getBrokerAdminService();
}
