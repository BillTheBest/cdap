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

import co.cask.cdap.api.common.RuntimeArguments;
import co.cask.cdap.api.spark.JavaSparkExecutionContext;
import co.cask.cdap.api.spark.JavaSparkMain;

import java.lang.reflect.Method;

/**
 * This class is a wrapper for the vanilla spark programs.
 */
public class SparkMainWrapper implements JavaSparkMain {
  private static final String ARGUMENT_STRING = ".program.args";

  @Override
  public void run(JavaSparkExecutionContext sec) throws Exception {
    String stageName = sec.getSpecification().getProperty(ExternalSparkProgram.STAGE_NAME);

    Class<?> mainClass = sec.getPluginContext().loadPluginClass(stageName);
    Method mainMethod = mainClass.getMethod("main", String[].class);
    Object[] objects = new Object[1];
    if (sec.getRuntimeArguments().containsKey(stageName + ARGUMENT_STRING)) {
      objects[0] = sec.getRuntimeArguments().get(stageName + ARGUMENT_STRING).split(" ");
    } else {
      objects[0] = RuntimeArguments.toPosixArray(sec.getRuntimeArguments());
    }
    mainMethod.invoke(null, objects);
  }
}
