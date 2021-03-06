/*
 * Copyright © 2015 Cask Data, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package co.cask.cdap.etl.common;

import co.cask.cdap.etl.api.Destroyable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Helper class to deal with {@link Destroyable}.
 */
public final class Destroyables {

  private static final Logger LOG = LoggerFactory.getLogger(Destroyables.class);

  public static void destroyQuietly(Destroyable destroyable) {
    try {
      destroyable.destroy();
    } catch (Throwable t) {
      LOG.warn("Exception when calling destroy on {}", destroyable, t);
    }
  }

  private Destroyables() {
  }
}
