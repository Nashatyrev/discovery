/*
 * SPDX-License-Identifier: Apache-2.0
 */

package org.ethereum.beacon.discovery.scheduler;

import java.util.concurrent.ScheduledExecutorService;

/**
 * The <code>ScheduledExecutorService</code> which functions based on the current system time
 * supplied by {@link TimeController#getTime()} instead of <code>System.currentTimeMillis()</code>
 */
public interface ControlledExecutorService extends ScheduledExecutorService {

  /**
   * Sets up the {@link TimeController} instance which manages ordered tasks execution and provides
   * current time
   */
  void setTimeController(TimeController timeController);
}
