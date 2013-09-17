/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.jclouds.chef.strategy;

import org.jclouds.chef.domain.Node;
import org.jclouds.chef.strategy.internal.ListNodesInEnvironmentImpl;

import com.google.common.base.Predicate;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.inject.ImplementedBy;

/**
 * 
 * 
 * @author Noorul Islam K M
 */
@ImplementedBy(ListNodesInEnvironmentImpl.class)
public interface ListNodesInEnvironment {

   public Iterable<? extends Node> execute(String environmentName);

   public Iterable<? extends Node> execute(String environmentName, Predicate<String> nodeNameSelector);

   public Iterable<? extends Node> execute(String environmentName, Iterable<String> toGet);

   public Iterable<? extends Node> execute(ListeningExecutorService executor, String environmentName);

   public Iterable<? extends Node> execute(ListeningExecutorService executor, String environmentName, Predicate<String> nodeNameSelector);

   public Iterable<? extends Node> execute(ListeningExecutorService executor, String environmentName, Iterable<String> toGet);
}
