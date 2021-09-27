/*
 * Copyright Camunda Services GmbH and/or licensed to Camunda Services GmbH under
 * one or more contributor license agreements. See the NOTICE file distributed
 * with this work for additional information regarding copyright ownership.
 * Licensed under the Zeebe Community License 1.1. You may not use this file
 * except in compliance with the Zeebe Community License 1.1.
 */
package io.camunda.zeebe.engine.state;

public enum ZbColumnFamilies {
  DEFAULT,

  // util
  KEY,

  // process
  PROCESS_VERSION,

  // process cache
  PROCESS_CACHE,
  PROCESS_CACHE_BY_ID_AND_VERSION,
  PROCESS_CACHE_DIGEST_BY_ID,

  // element instance
  ELEMENT_INSTANCE_PARENT_CHILD,
  ELEMENT_INSTANCE_KEY,

  NUMBER_OF_TAKEN_SEQUENCE_FLOWS,

  // variable state
  ELEMENT_INSTANCE_CHILD_PARENT,
  VARIABLES,

  // timer state
  TIMERS,
  TIMER_DUE_DATES,

  // pending deployments
  PENDING_DEPLOYMENT,
  DEPLOYMENT_RAW,

  // jobs
  JOBS,
  JOB_STATES,
  JOB_DEADLINES,
  JOB_ACTIVATABLE,

  // message
  MESSAGE_KEY,
  MESSAGES,
  MESSAGE_DEADLINES,
  MESSAGE_IDS,
  MESSAGE_CORRELATED,
  MESSAGE_PROCESSES_ACTIVE_BY_CORRELATION_KEY,
  MESSAGE_PROCESS_INSTANCE_CORRELATION_KEYS,

  // message subscription
  MESSAGE_SUBSCRIPTION_BY_KEY,
  @Deprecated // only used for migration logic
  MESSAGE_SUBSCRIPTION_BY_SENT_TIME,
  // migration end
  MESSAGE_SUBSCRIPTION_BY_NAME_AND_CORRELATION_KEY,

  // message start event subscription
  MESSAGE_START_EVENT_SUBSCRIPTION_BY_NAME_AND_KEY,
  MESSAGE_START_EVENT_SUBSCRIPTION_BY_KEY_AND_NAME,

  // process message subscription
  PROCESS_SUBSCRIPTION_BY_KEY,
  // migration start
  @Deprecated // only used for migration logic
  PROCESS_SUBSCRIPTION_BY_SENT_TIME,
  // migration end

  // incident
  INCIDENTS,
  INCIDENT_PROCESS_INSTANCES,
  INCIDENT_JOBS,

  // event
  EVENT_SCOPE,
  EVENT_TRIGGER,

  BLACKLIST,

  EXPORTER,

  AWAIT_WORKLOW_RESULT
}
