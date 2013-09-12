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
package org.jclouds.chef.suppliers;

import static org.jclouds.chef.suppliers.ChefVersionSupplier.DEFAULT_VERSION;
import static org.testng.Assert.assertEquals;

import org.testng.annotations.Test;

/**
 * Unit tests for the {@link ChefVersionSupplier} class.
 * 
 * @author Ignasi Barrera
 */
@Test(groups = "unit", testName = "ChefVersionSupplierTest")
public class ChefVersionSupplierTest {

   public void testReturnsDefaultVersion() {
      assertEquals(new ChefVersionSupplier("15").get(), DEFAULT_VERSION);
      assertEquals(new ChefVersionSupplier("0").get(), DEFAULT_VERSION);
      assertEquals(new ChefVersionSupplier("0.").get(), DEFAULT_VERSION);
   }

   public void testReturnsMajorVersionIfNotZero() {
      assertEquals(new ChefVersionSupplier("11.6").get().intValue(), 11);
      assertEquals(new ChefVersionSupplier("11.6.0").get().intValue(), 11);
      assertEquals(new ChefVersionSupplier("11.6.0.1").get().intValue(), 11);
   }

   public void testReturnsMinorVersionIfMajorIsZero() {
      assertEquals(new ChefVersionSupplier("0.9").get().intValue(), 9);
      assertEquals(new ChefVersionSupplier("0.9.8").get().intValue(), 9);
      assertEquals(new ChefVersionSupplier("0.9.8.2").get().intValue(), 9);
   }
}
