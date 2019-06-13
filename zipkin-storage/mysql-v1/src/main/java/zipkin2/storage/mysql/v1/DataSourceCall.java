/*
 * Copyright 2015-2019 The OpenZipkin Authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
package zipkin2.storage.mysql.v1;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.concurrent.Executor;
import java.util.function.Function;
import javax.sql.DataSource;
import org.jooq.DSLContext;
import zipkin2.Call;
import zipkin2.Callback;

/** Uncancelable call built with an executor */
final class DataSourceCall<V> extends Call.Base<V> {

  static final class Factory {
    final DataSource datasource;
    final DSLContexts context;
    final Executor executor;

    Factory(DataSource datasource, DSLContexts context, Executor executor) {
      this.datasource = datasource;
      this.context = context;
      this.executor = executor;
    }

    <V> DataSourceCall<V> create(Function<DSLContext, V> queryFunction) {
      return new DataSourceCall<>(this, queryFunction);
    }
  }

  final Factory factory;
  final Function<DSLContext, V> queryFunction;

  DataSourceCall(Factory factory, Function<DSLContext, V> queryFunction) {
    this.factory = factory;
    this.queryFunction = queryFunction;
  }

  @Override
  protected final V doExecute() throws IOException {
    try (Connection conn = factory.datasource.getConnection()) {
      DSLContext context = factory.context.get(conn);
      return queryFunction.apply(context);
    } catch (SQLException e) {
      throw new IOException(e);
    }
  }

  @Override
  protected void doEnqueue(Callback<V> callback) {
    class CallbackRunnable implements Runnable {
      @Override
      public void run() {
        try {
          callback.onSuccess(doExecute());
        } catch (IOException e) {
          // unwrap the exception
          if (e.getCause() instanceof SQLException) {
            callback.onError(e.getCause());
          } else {
            callback.onError(e);
          }
        } catch (Throwable t) {
          propagateIfFatal(t);
          callback.onError(t);
        }
      }
    }
    factory.executor.execute(new CallbackRunnable());
  }

  @Override
  public String toString() {
    return queryFunction.toString();
  }

  @Override
  public Call<V> clone() {
    return new DataSourceCall<>(factory, queryFunction);
  }
}
