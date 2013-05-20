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
package org.jclouds.chef.statements;

import java.io.IOException;
import java.util.Collections;

import javax.inject.Singleton;

import org.jclouds.scriptbuilder.domain.OsFamily;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.functionloader.FunctionLoader;
import org.jclouds.scriptbuilder.functionloader.FunctionNotFoundException;
import org.jclouds.scriptbuilder.functionloader.filters.LicenseHeaderFilter;
import org.jclouds.util.Strings2;

import com.google.common.base.Throwables;

/**
 * @author Adrian Cole
 */
@Singleton
public class InstallChefGems implements Statement {
   @Override
   public String render(OsFamily family) {
      return new LicenseHeaderFilter(new FunctionLoader() {
         @Override
         public String loadFunction(String function, OsFamily family) throws FunctionNotFoundException {
            try {
               return Strings2.toStringAndClose(getClass().getResourceAsStream(function));
            } catch (IOException e) {
               Throwables.propagate(e);
               return null;
            }
         }
      }).loadFunction("install-chef-gems.sh", family);
   }

   @Override
   public Iterable<String> functionDependencies(OsFamily family) {
      return Collections.emptyList();
   }

}
