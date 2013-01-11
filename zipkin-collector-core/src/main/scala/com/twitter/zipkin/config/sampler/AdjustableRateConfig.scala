/*
 * Copyright 2012 Twitter Inc.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.twitter.zipkin.config.sampler

import com.google.common.util.concurrent.AtomicDouble

trait AdjustableRateConfig {

  def get: Double

  def set(rate: Double)
}

class NullAdjustableRateConfig extends AdjustableRateConfig {

  def get: Double = 0.0

  def set(rate: Double) {}
}

class MutableAdjustableRateConfig(default: Double) extends AdjustableRateConfig {
  val value = new AtomicDouble(default)

  def get: Double = value.get()

  def set(rate: Double) = value.set(rate)
}

/**
 * Wrapper class around an AdjustableRateConfig that disallows
 * setting config values
 */
class ReadOnlyAdjustableRateConfig(config: AdjustableRateConfig)
extends AdjustableRateConfig {

  def get(): Double = config.get

  def set(rate: Double) {}

  def setIfNotExists(rate: Double) {}
}
