/*
 * Copyright © 2017 Cask Data, Inc.
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

package co.cask.cdap.datapipeline;

import co.cask.cdap.api.spark.AbstractSpark;

import java.util.HashMap;
import java.util.Map;

/**
 * A Spark program that instantiates a sparkprogram plugin and executes it. The plugin is a self contained
 * spark program.
 */
public class ExternalSparkProgram extends AbstractSpark {
  public static final String STAGE_NAME = "stage.name";
  private final String phaseName;
  private final String stageName;

  public ExternalSparkProgram(String phaseName, String stageName) {
    this.phaseName = phaseName;
    this.stageName = stageName;
  }

  @Override
  protected void configure() {
    setName(phaseName);
    setMainClass(SparkMainWrapper.class);

    Map<String, String> properties = new HashMap<>();
    properties.put(STAGE_NAME, stageName);
    setProperties(properties);
  }
}
