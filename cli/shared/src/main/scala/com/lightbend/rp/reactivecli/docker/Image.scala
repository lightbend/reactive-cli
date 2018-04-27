/*
 * Copyright 2017 Lightbend, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.lightbend.rp.reactivecli.docker

sealed trait ImageRef {
  def name: String
  def value: String
}

case class ImageDigest(value: String) extends ImageRef {
  def name: String = "digest"
}

case class ImageTag(value: String) extends ImageRef {
  def name: String = "tag"
}

case class Image(
  url: String,
  namespace: Option[String],
  image: String,
  ref: ImageRef,
  providedUrl: Option[String],
  providedNamespace: Option[String],
  providedImage: String,
  providedRef: Option[ImageRef]) {
  def pullScope: String = s"repository:${namespace.map(ns => s"$ns/")}$image:pull"
}
