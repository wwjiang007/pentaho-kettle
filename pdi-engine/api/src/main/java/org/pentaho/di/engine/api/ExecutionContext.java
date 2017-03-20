/*
 * ! ******************************************************************************
 *
 *  Pentaho Data Integration
 *
 *  Copyright (C) 2002-2017 by Pentaho : http://www.pentaho.com
 *
 * ******************************************************************************
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with
 *  the License. You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 * *****************************************************************************
 */

package org.pentaho.di.engine.api;

import org.pentaho.di.engine.api.converter.RowConversionManager;
import org.pentaho.di.engine.api.model.Transformation;
import org.pentaho.di.engine.api.reporting.SubscriptionManager;

import java.io.Serializable;
import java.security.Principal;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * Created by nbaker on 5/31/16.
 */
public interface ExecutionContext extends SubscriptionManager, Serializable {
  Map<String, Object> getParameters();

  /**
   * Implementors should return new instance of the ExecutionContext using
   * @param parameters
   */
  ExecutionContext withParameters( Map<String, Object> parameters );

  Map<String, Object> getEnvironment();

  Transformation getTransformation();

  CompletableFuture<ExecutionResult> execute();

  RowConversionManager getConversionManager();

  Principal getActingPrincipal();

  void setActingPrincipal( Principal actingPrincipal );
}
