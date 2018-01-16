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

package com.lightbend.rp.reactivecli

import scala.concurrent.{ ExecutionContext, Future, Promise }
import scala.util.{ Failure, Success, Try }

package object concurrent {
  implicit val executionContext: ExecutionContext = Platform.executionContext

  def attempt[T](f: Future[T]): Future[Try[T]] =
    f
      .map(Success.apply)
      .recover { case t: Throwable => Failure(t) }

  def optionToFuture[T](option: Option[T], failMsg: String): Future[T] =
    option.fold(Future.failed[T](new NoSuchElementException(failMsg)))(Future.successful)

  def wrapFutureOption[T](f: Future[T]): Future[Option[T]] = {
    val p = Promise[Option[T]]

    f.onComplete {
      case Failure(f) =>
        p.success(None)
      case Success(s) =>
        p.success(Some(s))
    }

    p.future
  }
}
