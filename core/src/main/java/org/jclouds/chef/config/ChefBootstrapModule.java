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
package org.jclouds.chef.config;

import static com.google.common.util.concurrent.MoreExecutors.listeningDecorator;
import static org.jclouds.chef.config.ChefProperties.CHEF_GEM_SYSTEM_VERSION;
import static org.jclouds.chef.config.ChefProperties.CHEF_UPDATE_GEMS;
import static org.jclouds.chef.config.ChefProperties.CHEF_UPDATE_GEM_SYSTEM;
import static org.jclouds.chef.config.ChefProperties.CHEF_USER_THREADS;
import static org.jclouds.chef.config.ChefProperties.CHEF_USE_OMNIBUS;
import static org.jclouds.chef.config.ChefProperties.CHEF_VERSION;
import static org.jclouds.concurrent.DynamicExecutors.newScalingThreadPool;

import com.google.common.base.Optional;
import com.google.common.util.concurrent.ListeningExecutorService;
import com.google.common.util.concurrent.MoreExecutors;
import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.inject.AbstractModule;
import com.google.inject.Inject;
import com.google.inject.Provides;
import org.jclouds.concurrent.config.WithSubmissionTrace;
import org.jclouds.lifecycle.Closer;
import org.jclouds.logging.Logger;
import org.jclouds.scriptbuilder.domain.Statement;
import org.jclouds.scriptbuilder.domain.StatementList;
import org.jclouds.scriptbuilder.statements.chef.InstallChefGems;
import org.jclouds.scriptbuilder.statements.chef.InstallChefUsingOmnibus;
import org.jclouds.scriptbuilder.statements.ruby.InstallRuby;
import org.jclouds.scriptbuilder.statements.ruby.InstallRubyGems;

import javax.annotation.Resource;
import javax.inject.Named;
import javax.inject.Singleton;
import java.io.Closeable;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;



/**
 * Provides bootstrap configuration for nodes.
 */
public class ChefBootstrapModule extends AbstractModule {

   final ListeningExecutorService chefUserExecutor;

   public ChefBootstrapModule() {
      this.chefUserExecutor = null;

   }

   public ChefBootstrapModule(@Named(CHEF_USER_THREADS) ExecutorService chefUserExecutor) {
      this(listeningDecorator(chefUserExecutor));
   }

   public ChefBootstrapModule(@Named(CHEF_USER_THREADS) ListeningExecutorService userExecutor) {
      this.chefUserExecutor = WithSubmissionTrace.wrap(userExecutor);
   }

   @Provides
   @Named("installChefGems")
   @Singleton
   Statement installChefGems(BootstrapProperties bootstrapProperties) {
      InstallRubyGems installRubyGems = InstallRubyGems.builder()
            .version(bootstrapProperties.gemSystemVersion().orNull())
            .updateSystem(bootstrapProperties.updateGemSystem(), bootstrapProperties.gemSystemVersion().orNull())
            .updateExistingGems(bootstrapProperties.updateGems()) //
            .build();

      Statement installChef = InstallChefGems.builder().version(bootstrapProperties.chefVersion().orNull()).build();

      return new StatementList(InstallRuby.builder().build(), installRubyGems, installChef);
   }

   @Provides
   @Named("installChefOmnibus")
   @Singleton
   Statement installChefUsingOmnibus() {
      return new InstallChefUsingOmnibus();
   }

   @Provides
   @InstallChef
   @Singleton
   Statement installChef(BootstrapProperties bootstrapProperties, @Named("installChefGems") Statement installChefGems,
         @Named("installChefOmnibus") Statement installChefOmnibus) {
      return bootstrapProperties.useOmnibus() ? installChefOmnibus : installChefGems;
   }

   @Provides
   @Singleton
   @Named(CHEF_USER_THREADS)
   ListeningExecutorService provideChefUserExecutorService(@Named(CHEF_USER_THREADS) int count,
         Closer closer) {
      if (chefUserExecutor != null)
         return chefUserExecutor;

      // Creates ListeningExecutorService
      String name = "chef user threads %d";
      ListeningExecutorService listeningExecutorService =
            MoreExecutors.listeningDecorator(count == 0 ?
                  Executors.newCachedThreadPool(namedThreadFactory(name)) :
                  newScalingThreadPool(1, count, 60L * 1000, namedThreadFactory(name)));
      closer.addToClose(new ShutdownExecutorOnClose(listeningExecutorService));
      return listeningExecutorService;

   }

   static final class ShutdownExecutorOnClose implements Closeable {
      @Resource
      private Logger logger = Logger.NULL;

      private final ListeningExecutorService service;

      private ShutdownExecutorOnClose(ListeningExecutorService service) {
         this.service = service;
      }

      @Override
      public void close() throws IOException {
         List<Runnable> runnables = service.shutdownNow();
         if (runnables.size() > 0)
            logger.warn("when shutting down executor %s, runnables outstanding: %s", service, runnables);
      }
   }

   private ThreadFactory namedThreadFactory(String name) {
      return new ThreadFactoryBuilder().setNameFormat(name).setThreadFactory(Executors.defaultThreadFactory()).build();
   }

   @Singleton
   private static class BootstrapProperties {
      @Named(CHEF_VERSION)
      @Inject(optional = true)
      private String chefVersionProperty;

      @Named(CHEF_GEM_SYSTEM_VERSION)
      @Inject(optional = true)
      private String gemSystemVersionProperty;

      @Named(CHEF_UPDATE_GEM_SYSTEM)
      @Inject
      private String updateGemSystemProeprty;

      @Named(CHEF_UPDATE_GEMS)
      @Inject
      private String updateGemsProperty;

      @Named(CHEF_USE_OMNIBUS)
      @Inject
      private String useOmnibus;

      public Optional<String> chefVersion() {
         return Optional.fromNullable(chefVersionProperty);
      }

      public Optional<String> gemSystemVersion() {
         return Optional.fromNullable(gemSystemVersionProperty);
      }

      public boolean updateGemSystem() {
         return Boolean.parseBoolean(updateGemSystemProeprty);
      }

      public boolean updateGems() {
         return Boolean.parseBoolean(updateGemsProperty);
      }

      public boolean useOmnibus() {
         return Boolean.parseBoolean(useOmnibus);
      }
   }

   @Override
   protected void configure() {
   }
}
