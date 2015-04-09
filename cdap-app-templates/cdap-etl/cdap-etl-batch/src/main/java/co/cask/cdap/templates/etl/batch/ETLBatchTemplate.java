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

package co.cask.cdap.templates.etl.batch;

import co.cask.cdap.api.templates.AdapterConfigurer;
import co.cask.cdap.api.templates.ApplicationTemplate;
import co.cask.cdap.internal.schedule.TimeSchedule;
import co.cask.cdap.templates.etl.api.StageSpecification;
import co.cask.cdap.templates.etl.api.Transform;
import co.cask.cdap.templates.etl.api.batch.BatchSink;
import co.cask.cdap.templates.etl.api.batch.BatchSource;
import co.cask.cdap.templates.etl.batch.sinks.KVTableSink;
import co.cask.cdap.templates.etl.batch.sources.KVTableSource;
import co.cask.cdap.templates.etl.common.Constants;
import co.cask.cdap.templates.etl.common.DefaultStageConfigurer;
import co.cask.cdap.templates.etl.common.config.ETLStage;
import co.cask.cdap.templates.etl.transforms.IdentityTransform;
import com.google.common.base.Preconditions;
import com.google.common.collect.Lists;
import com.google.common.collect.Maps;
import com.google.gson.Gson;

import java.util.List;
import java.util.Map;

/**
 * ETL Batch Template.
 */
public class ETLBatchTemplate extends ApplicationTemplate<ETLBatchConfig> {
  private static final Gson GSON = new Gson();
  private final Map<String, String> sourceClassMap;
  private final Map<String, String> sinkClassMap;
  private final Map<String, String> transformClassMap;

  public ETLBatchTemplate() throws Exception {
    sourceClassMap = Maps.newHashMap();
    sinkClassMap = Maps.newHashMap();
    transformClassMap = Maps.newHashMap();

    //TODO: Add classes from Lib here to be available for use in the ETL Adapter. Remove this when
    //plugins management is completed.
    initTable(Lists.<Class>newArrayList(KVTableSource.class, KVTableSink.class, IdentityTransform.class));
  }

  private void initTable(List<Class> classList) throws Exception {
    for (Class klass : classList) {
      DefaultStageConfigurer configurer = new DefaultStageConfigurer(klass);
      if (BatchSource.class.isAssignableFrom(klass)) {
        BatchSource source = (BatchSource) klass.newInstance();
        source.configure(configurer);
        sourceClassMap.put(configurer.createSpecification().getName(), configurer.createSpecification().getClassName());
      } else if (BatchSink.class.isAssignableFrom(klass)) {
        BatchSink sink = (BatchSink) klass.newInstance();
        sink.configure(configurer);
        sinkClassMap.put(configurer.createSpecification().getName(), configurer.createSpecification().getClassName());
      } else {
        Preconditions.checkArgument(Transform.class.isAssignableFrom(klass));
        Transform transform = (Transform) klass.newInstance();
        transform.configure(configurer);
        transformClassMap.put(configurer.createSpecification().getName(),
                              configurer.createSpecification().getClassName());
      }
    }
  }

  @Override
  public void configureAdapter(String adapterName, ETLBatchConfig etlConfig, AdapterConfigurer configurer)
    throws Exception {
    // Get cronEntry string for ETL Batch Adapter
    String cronEntry = etlConfig.getSchedule();

    ETLStage source = etlConfig.getSource();
    ETLStage sink = etlConfig.getSink();
    List<ETLStage> transform = etlConfig.getTransforms();

    configureSource(source, configurer);
    configureSink(sink, configurer);
    configureTransforms(transform, configurer);

    // Validate Adapter by making source the key-value types match.
    validateAdapter(source, sink, transform);

    configurer.addRuntimeArgument(Constants.ADAPTER_NAME, adapterName);
    configurer.addRuntimeArgument(Constants.CONFIG_KEY, GSON.toJson(etlConfig));
    configurer.setSchedule(new TimeSchedule(String.format("etl.batch.adapter.%s.schedule", adapterName),
                                            String.format("Schedule for %s Adapter", adapterName),
                                            cronEntry));
  }

  private void validateAdapter(ETLStage source, ETLStage sink, List<ETLStage> transform) throws Exception {
    String sourceName = source.getName();
    String sinkName = sink.getName();
    String sourceClassName = sourceClassMap.get(sourceName);
    String sinkClassName = sinkClassMap.get(sinkName);

    BatchSource batchSource = (BatchSource) Class.forName(sourceClassName).newInstance();
    BatchSink batchSink = (BatchSink) Class.forName(sinkClassName).newInstance();

    if (transform.size() == 0) {
      // No transforms. Check only source and sink.
      Preconditions.checkArgument(batchSink.getKeyType().getClass().isAssignableFrom(
        batchSource.getKeyType().getClass()));
      Preconditions.checkArgument(batchSink.getValueType().getClass().isAssignableFrom(
        batchSource.getValueType().getClass()));
    } else {
      // Check the first and last transform with source and sink.
      String firstTransformClassName = transformClassMap.get(transform.get(0).getName());
      String lastTransformClassName = transformClassMap.get(transform.get(transform.size() - 1).getName());
      Transform firstTransform = (Transform) Class.forName(firstTransformClassName).newInstance();
      Transform lastTransform = (Transform) Class.forName(lastTransformClassName).newInstance();

      Preconditions.checkArgument(firstTransform.getKeyInType().getClass().isAssignableFrom(
        batchSource.getKeyType().getClass()));
      Preconditions.checkArgument(firstTransform.getValueInType().getClass().isAssignableFrom(
        batchSource.getValueType().getClass()));
      Preconditions.checkArgument(lastTransform.getKeyOutType().getClass().isAssignableFrom(
        batchSink.getKeyType().getClass()));
      Preconditions.checkArgument(lastTransform.getValueOutType().getClass().isAssignableFrom(
        batchSink.getValueType().getClass()));
      if (transform.size() > 1) {
        // Check transform stages.
        validateTransforms(transform);
      }
    }
  }

  private void validateTransforms(List<ETLStage> transform) throws Exception {
    for (int i = 0; i < transform.size() - 1; i++) {
      String transform1 = transformClassMap.get(transform.get(i).getName());
      String transform2 = transformClassMap.get(transform.get(i + 1).getName());
      Transform firstTransform = (Transform) Class.forName(transform1).newInstance();
      Transform secondTransform = (Transform) Class.forName(transform2).newInstance();

      Preconditions.checkArgument(secondTransform.getKeyInType().getClass().isAssignableFrom(
        firstTransform.getKeyInType().getClass()));
      Preconditions.checkArgument(secondTransform.getValueInType().getClass().isAssignableFrom(
        firstTransform.getValueInType().getClass()));
      Preconditions.checkArgument(secondTransform.getKeyOutType().getClass().isAssignableFrom(
        firstTransform.getKeyOutType().getClass()));
      Preconditions.checkArgument(secondTransform.getValueOutType().getClass().isAssignableFrom(
        firstTransform.getValueOutType().getClass()));
    }
  }

  private void configureSource(ETLStage source, AdapterConfigurer configurer) throws Exception {
    String sourceName = source.getName();
    String className = sourceClassMap.get(sourceName);
    BatchSource batchSource = (BatchSource) Class.forName(className).newInstance();
    DefaultStageConfigurer stageConfigurer = new DefaultStageConfigurer(batchSource.getClass());
    StageSpecification specification = stageConfigurer.createSpecification();
    configurer.addRuntimeArgument(Constants.Source.SPECIFICATION, GSON.toJson(specification));
  }

  private void configureSink(ETLStage sink, AdapterConfigurer configurer) throws Exception {
    String sinkName = sink.getName();
    String className = sinkClassMap.get(sinkName);
    BatchSink batchSink = (BatchSink) Class.forName(className).newInstance();
    DefaultStageConfigurer stageConfigurer = new DefaultStageConfigurer(batchSink.getClass());
    StageSpecification specification = stageConfigurer.createSpecification();
    configurer.addRuntimeArgument(Constants.Sink.SPECIFICATION, GSON.toJson(specification));
  }

  private void configureTransforms(List<ETLStage> transforms, AdapterConfigurer configurer) throws Exception {
    List<StageSpecification> transformSpecs = Lists.newArrayList();
    for (ETLStage transform : transforms) {
      String transformName = transform.getName();
      String className = transformClassMap.get(transformName);
      Transform transformObj = (Transform) Class.forName(className).newInstance();
      DefaultStageConfigurer stageConfigurer = new DefaultStageConfigurer(transformObj.getClass());
      StageSpecification specification = stageConfigurer.createSpecification();
      transformSpecs.add(specification);
    }
    configurer.addRuntimeArgument(Constants.Transform.SPECIFICATIONS, GSON.toJson(transformSpecs));
  }

  @Override
  public void configure() {
    setName("etlbatch");
    setDescription("Batch Extract-Transform-Load (ETL) Adapter");
    addMapReduce(new ETLMapReduce());
    addWorkflow(new ETLWorkflow());
  }
}
