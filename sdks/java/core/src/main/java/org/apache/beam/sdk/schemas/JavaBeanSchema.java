/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.beam.sdk.schemas;

import com.google.common.annotations.VisibleForTesting;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.apache.beam.sdk.annotations.Experimental;
import org.apache.beam.sdk.annotations.Experimental.Kind;
import org.apache.beam.sdk.schemas.utils.FieldValueTypeSupplier;
import org.apache.beam.sdk.schemas.utils.JavaBeanUtils;
import org.apache.beam.sdk.schemas.utils.ReflectUtils;
import org.apache.beam.sdk.values.TypeDescriptor;

/**
 * A {@link SchemaProvider} for Java Bean objects.
 *
 * <p>This provider finds (recursively) all public getters and setters in a Java object, and creates
 * schemas and rows that bind to those fields. The field order in the schema is not guaranteed to
 * match the method order in the class. The Java object is expected to have implemented a correct
 * .equals() and .hashCode methods The equals method must be completely determined by the schema
 * fields. i.e. if the object has hidden fields that are not reflected in the schema but are
 * compared in equals, then results will be incorrect.
 *
 * <p>TODO: Validate equals() method is provided, and if not generate a "slow" equals method based
 * on the schema.
 */
@Experimental(Kind.SCHEMAS)
public class JavaBeanSchema extends GetterBasedSchemaProvider {
  /** {@link FieldValueTypeSupplier} that's based on getter methods. */
  @VisibleForTesting
  public static class GetterTypeSupplier implements FieldValueTypeSupplier {
    @Override
    public List<FieldValueTypeInformation> get(Class<?> clazz, Schema schema) {
      Map<String, FieldValueTypeInformation> types =
          ReflectUtils.getMethods(clazz)
              .stream()
              .filter(ReflectUtils::isGetter)
              .map(FieldValueTypeInformation::forGetter)
              .collect(Collectors.toMap(FieldValueTypeInformation::getName, Function.identity()));
      // Return the list ordered by the schema fields.
      return schema
          .getFields()
          .stream()
          .map(f -> types.get(f.getName()))
          .collect(Collectors.toList());
    }
  }

  /** {@link FieldValueTypeSupplier} that's based on setter methods. */
  @VisibleForTesting
  public static class SetterTypeSupplier implements FieldValueTypeSupplier {
    @Override
    public List<FieldValueTypeInformation> get(Class<?> clazz, Schema schema) {
      Map<String, FieldValueTypeInformation> types =
          ReflectUtils.getMethods(clazz)
              .stream()
              .filter(ReflectUtils::isSetter)
              .map(FieldValueTypeInformation::forSetter)
              .collect(Collectors.toMap(FieldValueTypeInformation::getName, Function.identity()));
      // Return the list ordered by the schema fields.
      return schema
          .getFields()
          .stream()
          .map(f -> types.get(f.getName()))
          .collect(Collectors.toList());
    }
  }

  @Override
  public <T> Schema schemaFor(TypeDescriptor<T> typeDescriptor) {
    return JavaBeanUtils.schemaFromJavaBeanClass(typeDescriptor.getRawType());
  }

  @Override
  public FieldValueGetterFactory fieldValueGetterFactory() {
    return (Class<?> targetClass, Schema schema) ->
        JavaBeanUtils.getGetters(targetClass, schema, new GetterTypeSupplier());
  }

  @Override
  UserTypeCreatorFactory schemaTypeCreatorFactory() {
    return new SetterBasedCreatorFactory(new JavaBeanSetterFactory());
  }

  @Override
  public FieldValueTypeInformationFactory fieldValueTypeInformationFactory() {
    return (Class<?> targetClass, Schema schema) ->
        JavaBeanUtils.getFieldTypes(targetClass, schema, new GetterTypeSupplier());
  }

  /** A factory for creating {@link FieldValueSetter} objects for a JavaBean object. */
  public static class JavaBeanSetterFactory implements FieldValueSetterFactory {
    @Override
    public List<FieldValueSetter> create(Class<?> targetClass, Schema schema) {
      return JavaBeanUtils.getSetters(targetClass, schema, new SetterTypeSupplier());
    }
  }
}
