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
package org.jclouds.chef.compute;
import static com.google.common.collect.Iterables.any;
import static com.google.common.collect.Iterables.concat;
import static com.google.common.collect.Iterables.getLast;
import static org.jclouds.chef.predicates.CookbookVersionPredicates.containsRecipe;
import static org.jclouds.compute.options.TemplateOptions.Builder.runScript;
import static org.jclouds.reflect.Reflection2.typeToken;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.util.List;

import org.jclouds.chef.ChefApi;
import org.jclouds.chef.ChefContext;
import org.jclouds.chef.compute.internal.BaseComputeServiceIntegratedChefClientLiveTest;
import org.jclouds.chef.domain.BootstrapConfig;
import org.jclouds.chef.domain.CookbookVersion;
import org.jclouds.chef.util.RunListBuilder;
import org.jclouds.compute.RunNodesException;
import org.jclouds.compute.domain.NodeMetadata;
import org.jclouds.compute.predicates.NodePredicates;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.util.Strings2;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import com.google.common.reflect.TypeToken;

/**
 * 
 * @author Adrian Cole
 */
@Test(groups = "live", testName = "ChefComputeServiceLiveTest")
public class ChefComputeServiceLiveTest extends BaseComputeServiceIntegratedChefClientLiveTest {

   private String group;
   private String clientName;
   private Iterable<? extends NodeMetadata> nodes;

   @Test
   public void testCanUpdateRunList() throws IOException {
      String recipe = "apache2";

      Iterable<? extends CookbookVersion> cookbookVersions = view.getChefService().listCookbookVersions();

      if (any(cookbookVersions, containsRecipe(recipe))) {
         List<String> runList = new RunListBuilder().addRecipe(recipe).build();
         BootstrapConfig bootstrap = BootstrapConfig.builder().runList(runList).build();
         view.getChefService().updateBootstrapConfigForGroup(group, bootstrap);
         assertEquals(view.getChefService().getRunListForGroup(group), runList);
      } else {
         fail(String.format("recipe %s not in %s", recipe, cookbookVersions));
      }
   }

   @Test(dependsOnMethods = "testCanUpdateRunList")
   public void testRunNodesWithBootstrap() throws IOException {

      Statement bootstrap = view.getChefService().createBootstrapScriptForGroup(group);

      try {
         nodes = computeContext.getComputeService().createNodesInGroup(group, 1, runScript(bootstrap));
      } catch (RunNodesException e) {
         nodes = concat(e.getSuccessfulNodes(), e.getNodeErrors().keySet());
      }

      for (NodeMetadata node : nodes) {
         URI uri = URI.create("http://" + getLast(node.getPublicAddresses()));
         InputStream content = computeContext.utils().http().get(uri);
         String string = Strings2.toStringAndClose(content);
         assertTrue(string.indexOf("It works!") >= 0, string);
      }
   }

   @AfterClass(groups = { "integration", "live" })
   @Override
   protected void tearDownContext() {
      if (computeContext != null) {
         computeContext.getComputeService().destroyNodesMatching(NodePredicates.inGroup(group));
      }
      if (context != null) {
         view.getChefService().cleanupStaleNodesAndClients(group + "-", 1);
         ChefApi api = view.unwrapApi(ChefApi.class);
         if (clientName != null && api.getClient(clientName) != null) {
            api.deleteClient(clientName);
         }
         context.close();
      }
      super.tearDownContext();
   }

   @Override
   protected TypeToken<ChefContext> viewType() {
      return typeToken(ChefContext.class);
   }

}
