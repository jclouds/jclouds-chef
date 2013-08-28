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
package org.jclouds.chef.internal;

import static com.google.common.base.Throwables.propagate;
import static com.google.common.collect.Iterables.isEmpty;
import static com.google.common.hash.Hashing.md5;
import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static org.jclouds.io.ByteSources.asByteSource;
import static org.jclouds.util.Predicates2.retry;
import static org.testng.Assert.assertEquals;
import static org.testng.Assert.assertFalse;
import static org.testng.Assert.assertNotNull;
import static org.testng.Assert.assertTrue;
import static org.testng.Assert.fail;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.Properties;
import java.util.Set;

import org.jclouds.chef.ChefApi;
import org.jclouds.chef.domain.ChecksumStatus;
import org.jclouds.chef.domain.Client;
import org.jclouds.chef.domain.CookbookVersion;
import org.jclouds.chef.domain.DatabagItem;
import org.jclouds.chef.domain.Environment;
import org.jclouds.chef.domain.Metadata;
import org.jclouds.chef.domain.Node;
import org.jclouds.chef.domain.Resource;
import org.jclouds.chef.domain.Role;
import org.jclouds.chef.domain.SearchResult;
import org.jclouds.chef.domain.UploadSandbox;
import org.jclouds.chef.options.CreateClientOptions;
import org.jclouds.chef.options.SearchOptions;
import org.jclouds.crypto.Pems;
import org.jclouds.io.Payloads;
import org.jclouds.io.payloads.FilePayload;
import org.jclouds.rest.ResourceNotFoundException;
import org.testng.annotations.AfterClass;
import org.testng.annotations.Test;

import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.io.Closeables;
import com.google.common.primitives.Bytes;

/**
 * Tests behavior of {@code ChefApi}
 * 
 * @author Adrian Cole
 */
@Test(groups = { "live", "integration" })
public abstract class BaseChefApiLiveTest<A extends ChefApi> extends BaseChefLiveTest<A> {
   public static final String PREFIX = "jcloudstest-" + System.getProperty("user.name");
   public static final String ADMIN_PREFIX = "jcloudstest-adm-" + System.getProperty("user.name");

   // It may take a bit until the search index is populated
   protected int maxWaitForIndexInMs = 60000;

   private Node node;
   private Role role;
   protected DatabagItem databagItem;

   public void testCreateNewCookbook() throws Exception {
      // Define the file you want in the cookbook
      FilePayload content = Payloads.newFilePayload(new File(System.getProperty("user.dir"), "pom.xml"));
      content.getContentMetadata().setContentType("application/x-binary");

      // Get an md5 so that you can see if the server already has it or not
      Payloads.calculateMD5(content);

      // Note that java collections cannot effectively do equals or hashcodes on
      // byte arrays, so let's convert to a list of bytes.
      List<Byte> md5 = Bytes.asList(content.getContentMetadata().getContentMD5());

      // Request an upload site for this file
      UploadSandbox site = api.getUploadSandboxForChecksums(ImmutableSet.of(md5));
      assertTrue(site.getChecksums().containsKey(md5), md5 + " not in " + site.getChecksums());

      try {
         // Upload the file contents, if still not uploaded
         ChecksumStatus status = site.getChecksums().get(md5);
         if (status.needsUpload()) {
            api.uploadContent(status.getUrl(), content);
         }
         api.commitSandbox(site.getSandboxId(), true);
      } catch (RuntimeException e) {
         api.commitSandbox(site.getSandboxId(), false);
         fail("Could not upload content");
      }

      // Create the metadata of the cookbook
      Metadata metadata = Metadata.builder() //
            .name(PREFIX) //
            .version("0.0.0") //
            .description("Jclouds test uploaded cookbook") //
            .maintainer("jclouds") //
            .maintainerEmail("someone@jclouds.org") //
            .license("Apache 2.0") //
            .build();

      // Create a new cookbook
      CookbookVersion cookbook = CookbookVersion.builder(PREFIX, "0.0.0") //
            .metadata(metadata) //
            .rootFile(Resource.builder().fromPayload(content).build()) //
            .build();

      // upload the cookbook to the remote server
      api.updateCookbook(PREFIX, "0.0.0", cookbook);
   }

   public void testListCookbooks() throws Exception {
      Set<String> cookbookNames = api.listCookbooks();
      assertFalse(cookbookNames.isEmpty(), "No cookbooks were found");

      for (String cookbookName : cookbookNames) {
         Set<String> versions = api.getVersionsOfCookbook(cookbookName);
         assertFalse(versions.isEmpty(), "There are no versions of the cookbook: " + cookbookName);

         for (String version : api.getVersionsOfCookbook(cookbookName)) {
            CookbookVersion cookbook = api.getCookbook(cookbookName, version);
            assertNotNull(cookbook, "Could not get cookbook: " + cookbookName);
         }
      }
   }

   @Test(dependsOnMethods = "testListCookbooks")
   public void testListCookbookVersionsWithChefService() throws Exception {
      Iterable<? extends CookbookVersion> cookbooks = chefService.listCookbookVersions();
      assertFalse(isEmpty(cookbooks), "No cookbooks were found");
   }

   @Test(dependsOnMethods = "testListCookbookVersionsWithChefService")
   public void testDownloadCookbooks() throws Exception {
      Iterable<? extends CookbookVersion> cookbooks = chefService.listCookbookVersions();
      for (CookbookVersion cookbook : cookbooks) {
         for (Resource resource : ImmutableList.<Resource> builder().addAll(cookbook.getDefinitions())
               .addAll(cookbook.getFiles()).addAll(cookbook.getLibraries()).addAll(cookbook.getSuppliers())
               .addAll(cookbook.getRecipes()).addAll(cookbook.getResources()).addAll(cookbook.getRootFiles())
               .addAll(cookbook.getTemplates()).build()) {

            InputStream stream = api.getResourceContents(resource);
            assertNotNull(cookbook, "Resource contents are null for resource: " + resource.getName());

            byte[] md5 = asByteSource(stream).hash(md5()).asBytes();
            assertEquals(md5, resource.getChecksum());
         }
      }
   }

   @Test(dependsOnMethods = "testCreateNewCookbook")
   public void testUpdateCookbook() throws Exception {
      CookbookVersion cookbook = api.getCookbook(PREFIX, "0.0.0");
      assertNotNull(cookbook, "Cookbook not found: " + PREFIX);
      assertNotNull(api.updateCookbook(PREFIX, "0.0.0", cookbook), "Updated cookbook was null");
   }

   @Test(dependsOnMethods = { "testCreateNewCookbook", "testUpdateCookbook" })
   public void testDeleteCookbook() throws Exception {
      assertNotNull(api.deleteCookbook(PREFIX, "0.0.0"), "Deleted cookbook was null");
   }

   @Test
   public void testCreateClient() throws Exception {
      api.deleteClient(PREFIX);
      String credential = Pems.pem(api.createClient(PREFIX).getPrivateKey());
      assertClientCreated(PREFIX, credential);
   }

   @Test
   public void testCreateAdminClient() throws Exception {
      api.deleteClient(ADMIN_PREFIX);
      String credential = Pems.pem(api.createClient(ADMIN_PREFIX, CreateClientOptions.Builder.admin()).getPrivateKey());
      assertClientCreated(ADMIN_PREFIX, credential);
   }

   @Test(dependsOnMethods = "testCreateClient")
   public void testGenerateKeyForClient() throws Exception {
      String credential = Pems.pem(api.generateKeyForClient(PREFIX).getPrivateKey());
      assertClientCreated(PREFIX, credential);
   }

   @Test
   public void testListNodes() throws Exception {
      Set<String> nodes = api.listNodes();
      assertNotNull(nodes, "No nodes were found");
   }

   @Test(dependsOnMethods = "testCreateRole")
   public void testCreateNode() throws Exception {
      api.deleteNode(PREFIX);
      api.createNode(Node.builder().name(PREFIX).runListElement("role[" + PREFIX + "]").environment("_default").build());
      node = api.getNode(PREFIX);
      // TODO check recipes
      assertNotNull(node, "Created node should not be null");
      Set<String> nodes = api.listNodes();
      assertTrue(nodes.contains(PREFIX), String.format("node %s not in %s", PREFIX, nodes));
   }

   @Test(dependsOnMethods = "testCreateNode")
   public void testUpdateNode() throws Exception {
      for (String nodename : api.listNodes()) {
         Node node = api.getNode(nodename);
         api.updateNode(node);
      }
   }

   @Test
   public void testListRoles() throws Exception {
      Set<String> roles = api.listRoles();
      assertNotNull(roles, "Role list was null");
   }

   @Test
   public void testCreateRole() throws Exception {
      api.deleteRole(PREFIX);
      api.createRole(Role.builder().name(PREFIX).runListElement("recipe[java]").build());
      role = api.getRole(PREFIX);
      assertNotNull(role, "Created role should not be null");
      assertEquals(role.getName(), PREFIX);
      assertEquals(role.getRunList(), Collections.singleton("recipe[java]"));
   }

   @Test(dependsOnMethods = "testCreateRole")
   public void testUpdateRole() throws Exception {
      for (String rolename : api.listRoles()) {
         Role role = api.getRole(rolename);
         api.updateRole(role);
      }
   }

   @Test
   public void testListDatabags() throws Exception {
      Set<String> databags = api.listDatabags();
      assertNotNull(databags, "Data bag list was null");
   }

   @Test
   public void testCreateDatabag() throws Exception {
      api.deleteDatabag(PREFIX);
      api.createDatabag(PREFIX);
   }

   @Test(dependsOnMethods = "testCreateDatabagItem")
   public void testListDatabagItems() throws Exception {
      Set<String> databagItems = api.listDatabagItems(PREFIX);
      assertNotNull(databagItems, "Data bag item list was null");
   }

   @Test(dependsOnMethods = "testCreateDatabag")
   public void testCreateDatabagItem() throws Exception {
      Properties config = new Properties();
      config.setProperty("foo", "bar");
      api.deleteDatabagItem(PREFIX, PREFIX);
      databagItem = api.createDatabagItem(PREFIX, new DatabagItem("config", json.toJson(config)));
      assertNotNull(databagItem, "Created data bag item should not be null");
      assertEquals(databagItem.getId(), "config");

      // The databagItem json contains extra keys: (the name and the type if the
      // item)
      Properties props = json.fromJson(databagItem.toString(), Properties.class);
      for (Object key : config.keySet()) {
         assertTrue(props.containsKey(key));
         assertEquals(config.get(key), props.get(key));
      }
   }

   @Test(dependsOnMethods = "testCreateDatabagItem")
   public void testUpdateDatabagItem() throws Exception {
      for (String databagItemId : api.listDatabagItems(PREFIX)) {
         DatabagItem databagItem = api.getDatabagItem(PREFIX, databagItemId);
         api.updateDatabagItem(PREFIX, databagItem);
      }
   }

   @Test
   public void testListSearchIndexes() throws Exception {
      Set<String> indexes = api.listSearchIndexes();
      assertNotNull(indexes, "The index list should not be null");
      assertTrue(indexes.contains("node"));
      assertTrue(indexes.contains("client"));
      assertTrue(indexes.contains("role"));
   }

   @Test
   public void testSearchNodes() throws Exception {
      SearchResult<? extends Node> results = api.searchNodes();
      assertNotNull(results, "Node result list should not be null");
   }

   @Test(dependsOnMethods = { "testListSearchIndexes", "testCreateNode" })
   public void testSearchNodesWithOptions() throws Exception {
      Predicate<SearchOptions> waitForIndex = retry(new Predicate<SearchOptions>() {
         @Override
         public boolean apply(SearchOptions input) {
            SearchResult<? extends Node> results = api.searchNodes(input);
            assertNotNull(results);
            if (results.size() > 0) {
               assertEquals(results.size(), 1);
               assertEquals(results.iterator().next().getName(), PREFIX);
               return true;
            } else {
               // The index may still not be populated
               return false;
            }
         }
      }, maxWaitForIndexInMs, 5000L, MILLISECONDS);

      SearchOptions options = SearchOptions.Builder.query("name:" + PREFIX);
      assertTrue(waitForIndex.apply(options));
   }

   @Test
   public void testSearchClients() throws Exception {
      SearchResult<? extends Client> results = api.searchClients();
      assertNotNull(results, "Client result list should not be null");
   }

   @Test(dependsOnMethods = { "testListSearchIndexes", "testCreateClient" })
   public void testSearchClientsWithOptions() throws Exception {
      Predicate<SearchOptions> waitForIndex = retry(new Predicate<SearchOptions>() {
         @Override
         public boolean apply(SearchOptions input) {
            SearchResult<? extends Client> results = api.searchClients(input);
            assertNotNull(results);
            if (results.size() > 0) {
               assertEquals(results.size(), 1);
               assertEquals(results.iterator().next().getName(), PREFIX);
               return true;
            } else {
               // The index may still not be populated
               return false;
            }
         }
      }, maxWaitForIndexInMs, 5000L, MILLISECONDS);

      SearchOptions options = SearchOptions.Builder.query("name:" + PREFIX);
      assertTrue(waitForIndex.apply(options));
   }

   @Test
   public void testSearchRoles() throws Exception {
      SearchResult<? extends Role> results = api.searchRoles();
      assertNotNull(results, "Role result list should not be null");
   }

   @Test(dependsOnMethods = { "testListSearchIndexes", "testCreateRole" })
   public void testSearchRolesWithOptions() throws Exception {
      Predicate<SearchOptions> waitForIndex = retry(new Predicate<SearchOptions>() {
         @Override
         public boolean apply(SearchOptions input) {
            SearchResult<? extends Role> results = api.searchRoles(input);
            assertNotNull(results);
            if (results.size() > 0) {
               assertEquals(results.size(), 1);
               assertEquals(results.iterator().next().getName(), PREFIX);
               return true;
            } else {
               // The index may still not be populated
               return false;
            }
         }
      }, maxWaitForIndexInMs, 5000L, MILLISECONDS);

      SearchOptions options = SearchOptions.Builder.query("name:" + PREFIX);
      assertTrue(waitForIndex.apply(options));
   }

   @Test(dependsOnMethods = { "testListSearchIndexes", "testCreateDatabagItem" })
   public void testSearchDatabag() throws Exception {
      SearchResult<? extends DatabagItem> results = api.searchDatabag(PREFIX);
      assertNotNull(results, "Data bag item result list should not be null");
   }

   @Test(dependsOnMethods = { "testListSearchIndexes", "testCreateDatabagItem" })
   public void testSearchDatabagWithOptions() throws Exception {
      Predicate<SearchOptions> waitForIndex = retry(new Predicate<SearchOptions>() {
         @Override
         public boolean apply(SearchOptions input) {
            SearchResult<? extends DatabagItem> results = api.searchDatabag(PREFIX, input);
            assertNotNull(results);
            if (results.size() > 0) {
               assertEquals(results.size(), 1);
               assertEquals(results.iterator().next().getId(), databagItem.getId());
               return true;
            } else {
               // The index may still not be populated
               return false;
            }
         }
      }, maxWaitForIndexInMs, 5000L, MILLISECONDS);

      SearchOptions options = SearchOptions.Builder.query("id:" + databagItem.getId());
      assertTrue(waitForIndex.apply(options));
   }

   @Test(expectedExceptions = ResourceNotFoundException.class, dependsOnMethods = "testListSearchIndexes")
   public void testSearchDatabagNotFound() throws Exception {
      SearchResult<? extends DatabagItem> results = api.searchDatabag("whoopie");
      assertNotNull(results, "Data bag item result list should not be null");
   }

   @Test
   public void testCreateEnvironment() {
      api.deleteEnvironment(PREFIX);
      api.createEnvironment(Environment.builder().name(PREFIX).description(PREFIX).build());
      Environment env = api.getEnvironment(PREFIX);
      assertNotNull(env, "Created environment should not be null");
      assertEquals(env.getName(), PREFIX);
      assertEquals(env.getDescription(), PREFIX);
   }

   @Test(dependsOnMethods = "testCreateEnvironment")
   public void testListEnvironment() {
      Set<String> envList = api.listEnvironments();
      assertNotNull(envList, "Environment list was null");
      assertTrue(envList.contains(PREFIX));
   }

   @Test(dependsOnMethods = "testCreateEnvironment")
   public void testSearchEnvironments() throws Exception {
      SearchResult<? extends Environment> results = api.searchEnvironments();
      assertNotNull(results, "Environment result list was null");
   }

   @Test(dependsOnMethods = { "testListSearchIndexes", "testCreateEnvironment" })
   public void testSearchEnvironmentsWithOptions() throws Exception {
      Predicate<SearchOptions> waitForIndex = retry(new Predicate<SearchOptions>() {
         @Override
         public boolean apply(SearchOptions input) {
            SearchResult<? extends Environment> results = api.searchEnvironments(input);
            assertNotNull(results);
            if (results.size() > 0) {
               assertEquals(results.size(), 1);
               assertEquals(results.iterator().next().getName(), PREFIX);
               return true;
            } else {
               // The index may still not be populated
               return false;
            }
         }
      }, maxWaitForIndexInMs, 5000L, MILLISECONDS);

      SearchOptions options = SearchOptions.Builder.query("name:" + PREFIX);
      assertTrue(waitForIndex.apply(options));
   }

   @AfterClass(groups = { "live", "integration" })
   @Override
   public void tearDown() {
      api.deleteClient(PREFIX);
      api.deleteClient(ADMIN_PREFIX);
      api.deleteNode(PREFIX);
      api.deleteRole(PREFIX);
      api.deleteDatabag(PREFIX);
      api.deleteEnvironment(PREFIX);
      super.tearDown();
   }

   private void assertClientCreated(String identity, String credential) {
      Properties overrides = super.setupProperties();
      overrides.setProperty(provider + ".identity", identity);
      overrides.setProperty(provider + ".credential", credential);

      A clientApi = create(overrides, setupModules());

      try {
         Client client = clientApi.getClient(identity);
         assertNotNull(client, "Client not found: " + identity);
      } finally {
         try {
            Closeables.close(clientApi, true);
         } catch (IOException e) {
            throw propagate(e);
         }
      }
   }

}
